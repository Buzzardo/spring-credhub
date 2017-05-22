/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.credhub.core;

import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import org.springframework.credhub.support.CredentialDetails;
import org.springframework.credhub.support.CredentialDetailsData;
import org.springframework.credhub.support.ValueCredential;
import org.springframework.credhub.support.ValueType;
import org.springframework.credhub.support.ValueCredentialRequest;
import org.springframework.credhub.support.CredentialRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RunWith(Theories.class)
public class CredHubTemplateDetailValueUnitTests
		extends CredHubTemplateDetailUnitTestsBase<ValueCredential> {
	private static final ValueCredential CREDENTIAL = new ValueCredential("secret");


	@DataPoints("detail-responses")
	public static List<ResponseEntity<CredentialDetails<ValueCredential>>> buildDetailResponses() {
		return buildDetailResponses(ValueType.VALUE, CREDENTIAL);
	}

	@DataPoints("data-responses")
	public static List<ResponseEntity<CredentialDetailsData<ValueCredential>>> buildDataResponses() {
		return buildDataResponses(ValueType.VALUE, CREDENTIAL);
	}

	@Override
	public CredentialRequest<ValueCredential> getRequest() {
		return ValueCredentialRequest.builder()
				.name(NAME)
				.value(CREDENTIAL)
				.build();
	}

	@Override
	public Class<ValueCredential> getType() {
		return ValueCredential.class;
	}

	@Theory
	public void write(@FromDataPoints("detail-responses")
					  ResponseEntity<CredentialDetails<ValueCredential>> expectedResponse) {
		verifyWrite(expectedResponse);
	}

	@Theory
	public void getById(@FromDataPoints("detail-responses")
						ResponseEntity<CredentialDetails<ValueCredential>> expectedResponse) {
		verifyGetById(expectedResponse);
	}

	@Theory
	public void getByNameWithString(@FromDataPoints("data-responses")
									ResponseEntity<CredentialDetailsData<ValueCredential>> expectedResponse) {
		verifyGetByNameWithString(expectedResponse);
	}

	@Theory
	public void getByNameWithCredentialName(@FromDataPoints("data-responses")
											ResponseEntity<CredentialDetailsData<ValueCredential>> expectedResponse) {
		verifyGetByNameWithCredentialName(expectedResponse);
	}
}