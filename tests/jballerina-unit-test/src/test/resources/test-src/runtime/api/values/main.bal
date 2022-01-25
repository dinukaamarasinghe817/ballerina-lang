// Copyright (c) 2021 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import testorg/runtime_api.records;
import testorg/runtime_api.objects;
import testorg/runtime_api.maps;
import ballerina/lang.test as test;

public function main() {

    objects:Person p = <objects:Person>objects:getObject("Person");
    records:Address address = <records:Address>records:getRecord("Address");

    test:assertValueEqual(p.getPersonCity(), "Nugegoda");
    test:assertValueEqual(address.city, "Nugegoda");

    // Negative Tests
    object{}|error invalidObject = trap objects:getObject("Person2");
    record{}|error invalidRecord = trap records:getRecord("Address2");

    test:assertTrue(invalidObject is error);
    test:assertTrue(invalidRecord is error);

    error e1 = <error>invalidObject;
    error e2 = <error>invalidRecord;

    test:assertValueEqual(e1.message(), "No such object: Person2");
    test:assertValueEqual(e2.message(), "No such record: Address2");

    records:Foo|error fooOrError =  trap <records:Foo> records:getRecordNegative("Foo");
    test:assertTrue(fooOrError is error);
    error e3 = <error> fooOrError;
    test:assertValueEqual(e3.message(), "value creation with 'class java.util.ArrayList' not supported: expected a BValue");

    records:Bar|error barOrError = trap <records:Bar> records:getReadonlyRecordNegative("Bar");
    test:assertTrue(barOrError is error);
    error e4 = <error> barOrError;
    test:assertValueEqual(e4.message(), "value creation with 'class java.util.ImmutableCollections$MapN' not supported: expected a BValue");

    record{}|error res = trap records:getRecordWithRestFieldsNegative();
    test:assertTrue(res is error);
    error e5 = <error> res;
    test:assertValueEqual(e5.message(), "value creation with 'class java.lang.String' not supported: expected a BValue");

    maps:validateAPI();
    records:validateAPI();
}
