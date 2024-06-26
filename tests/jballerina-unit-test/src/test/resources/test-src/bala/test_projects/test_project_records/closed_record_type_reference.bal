// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import testOrg/public_and_private_types;

public type BClosedPerson record {|
    string name = "anonymous";
    int age = 0;
    ClosedAddress adr = {city:"", country:""};
|};

public type BClosedManager record {|
    *BClosedEmployee;
    string dept = "";
|};

public type BClosedEmployee record {|
    string company = "";
    *BClosedPerson;
|};

public type ClosedAddress record {|
    string city;
    string country;
|};

public type ClosedVehicleWithNever record {|
    int j;
    never p?;
|};

public type BClosedStudent record {|
    string name = "anonymous";
    int age = 20;
|};

public type Info record {|
    *public_and_private_types:Person;
|};

public type Info1 record {|
    string name = "James";
    *public_and_private_types:Person;
|};

public type Location record {
    *public_and_private_types:Address;
    string street = "abc";
    int zipCode = 123;
};
