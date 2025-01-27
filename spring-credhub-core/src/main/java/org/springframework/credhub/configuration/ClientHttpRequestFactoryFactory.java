/*
 * Copyright 2016-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.credhub.configuration;

import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient.Builder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;

import org.springframework.credhub.support.ClientOptions;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Factory for {@link ClientHttpRequestFactory} that supports Apache HTTP Components,
 * OkHttp the JDK HTTP client (in that order). This factory configures a
 * {@link ClientHttpRequestFactory} depending on the available dependencies.
 *
 * @author Mark Paluch
 * @author Scott Frederick
 */
public final class ClientHttpRequestFactoryFactory {

	private static final Log logger = LogFactory.getLog(ClientHttpRequestFactoryFactory.class);

	private static final SslCertificateUtils sslCertificateUtils = new SslCertificateUtils();

	private static final boolean HTTP_COMPONENTS_PRESENT = ClassUtils.isPresent(
			"org.apache.hc.client5.http.impl.classic.HttpClients",
			ClientHttpRequestFactoryFactory.class.getClassLoader());

	private static final boolean OKHTTP3_PRESENT = ClassUtils.isPresent("okhttp3.OkHttpClient",
			ClientHttpRequestFactoryFactory.class.getClassLoader());

	private ClientHttpRequestFactoryFactory() {
	}

	/**
	 * Create a {@link ClientHttpRequestFactory} for the given {@link ClientOptions}.
	 * @param options must not be {@literal null}
	 * @return a new {@link ClientHttpRequestFactory}. Lifecycle beans must be initialized
	 * after obtaining.
	 */
	public static ClientHttpRequestFactory create(ClientOptions options) {

		Assert.notNull(options, "ClientOptions must not be null");

		try {
			if (HTTP_COMPONENTS_PRESENT) {
				logger.info("Using Apache HttpComponents HttpClient for HTTP connections");
				return HttpComponents.usingHttpComponents(options);
			}

			if (OKHTTP3_PRESENT) {
				logger.info("Using OkHttp3 for HTTP connections");
				return OkHttp3.usingOkHttp3(options);
			}
		}
		catch (GeneralSecurityException ex) {
			logger.warn("Error configuring HTTP connections", ex);
		}

		logger.info("Defaulting to java.net.HttpUrlConnection for HTTP connections");
		return HttpURLConnection.usingJdk(options);
	}

	private static boolean usingCustomCerts(ClientOptions options) {
		return options.getCaCertFiles() != null;
	}

	/**
	 * {@link ClientHttpRequestFactory} using {@link java.net.HttpURLConnection}.
	 */
	static class HttpURLConnection {

		static ClientHttpRequestFactory usingJdk(ClientOptions options) {
			if (usingCustomCerts(options)) {
				logger.warn("Trust material will not be configured when using "
						+ "java.net.HttpUrlConnection. Use an alternate HTTP Client "
						+ "(Apache HttpComponents HttpClient or OkHttp3) when configuring CA certificates.");
			}

			SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

			if (options.getConnectionTimeout() != null) {
				factory.setConnectTimeout(options.getConnectionTimeoutMillis());
			}
			if (options.getReadTimeout() != null) {
				factory.setReadTimeout(options.getReadTimeoutMillis());
			}

			return factory;
		}

	}

	/**
	 * {@link ClientHttpRequestFactory} using Apache HttpComponents.
	 *
	 * @author Mark Paluch
	 * @author Scott Frederick
	 */
	static class HttpComponents {

		static ClientHttpRequestFactory usingHttpComponents(ClientOptions options) throws GeneralSecurityException {

			SocketConfig.Builder socketConfigBuilder = SocketConfig.custom();
			if (options.getReadTimeout() != null) {
				socketConfigBuilder.setSoTimeout(Timeout.ofMilliseconds(options.getReadTimeoutMillis()));
			}
			SocketConfig socketConfig = socketConfigBuilder.build();

			HttpClientBuilder httpClientBuilder = HttpClients.custom();

			if (usingCustomCerts(options)) {
				SSLContext sslContext = sslCertificateUtils.getSSLContext(options.getCaCertFiles());
				SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
				PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder
						.create().setSSLSocketFactory(sslSocketFactory).setDefaultSocketConfig(socketConfig).build();
				httpClientBuilder.setConnectionManager(connectionManager);
			}
			else {
				SSLContext sslContext = SSLContext.getDefault();
				SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
				PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder
						.create().useSystemProperties().setSSLSocketFactory(sslSocketFactory)
						.setDefaultSocketConfig(socketConfig).build();
				httpClientBuilder.setConnectionManager(connectionManager);
			}

			RequestConfig.Builder requestConfigBuilder = RequestConfig.custom().setAuthenticationEnabled(true);

			if (options.getConnectionTimeout() != null) {
				requestConfigBuilder
						.setConnectTimeout(Timeout.ofMilliseconds(options.getConnectionTimeout().toMillis()));
			}
			httpClientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());

			return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
		}

	}

	/**
	 * {@link ClientHttpRequestFactory} using {@link OkHttp3}.
	 *
	 * @author Mark Paluch
	 * @author Scott Frederick
	 */
	static class OkHttp3 {

		static ClientHttpRequestFactory usingOkHttp3(ClientOptions options) throws GeneralSecurityException {

			Builder builder = new Builder();

			if (usingCustomCerts(options)) {
				SSLSocketFactory socketFactory = sslCertificateUtils.getSSLContext(options.getCaCertFiles())
						.getSocketFactory();
				X509TrustManager trustManager = sslCertificateUtils.createTrustManager(options.getCaCertFiles());

				builder.sslSocketFactory(socketFactory, trustManager);
			}
			else {
				SSLSocketFactory socketFactory = SSLContext.getDefault().getSocketFactory();
				X509TrustManager trustManager = sslCertificateUtils.getDefaultX509TrustManager();

				builder.sslSocketFactory(socketFactory, trustManager);
			}

			if (options.getConnectionTimeout() != null) {
				builder.connectTimeout(options.getConnectionTimeoutMillis(), TimeUnit.MILLISECONDS);
			}
			if (options.getReadTimeout() != null) {
				builder.readTimeout(options.getReadTimeoutMillis(), TimeUnit.MILLISECONDS);
			}

			return new OkHttp3ClientHttpRequestFactory(builder.build());
		}

	}

}
