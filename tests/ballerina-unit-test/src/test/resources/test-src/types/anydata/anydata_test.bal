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

type Foo record {
    int a;
    anydata... // TODO: Remove this line once the default rest field type is changed to anydata
};

type ClosedFoo record {
    int ca;
    !...
};

type Employee record {
    int id;
    string name;
    float salary;
    !...
};

function testLiteralValueAssignment() returns (anydata, anydata, anydata, anydata) {
    anydata adi = 10;
    anydata adf = 23.45;
    anydata adb = true;
    anydata ads = "Hello World!";
    return (adi, adf, adb, ads);
}

function testValueTypesAssignment() returns (anydata, anydata, anydata, anydata) {
    int i = 10;
    anydata adi = i;

    float f = 23.45;
    anydata adf = f;

    boolean b = true;
    anydata adb = b;

    string s = "Hello World!";
    anydata ads = s;

    return (adi, adf, adb, ads);
}

function testRecordAssignment() returns (anydata, anydata) {
    Foo f = {a: 20};
    anydata adr = f;

    ClosedFoo cf = {ca: 35};
    anydata adcr = cf;

    return (adr, adcr);
}

function testXMLAssignment() returns (anydata, anydata) {
    anydata adxl = xml `<book>The Lost World</book>`;

    xml x = xml `<book>Count of Monte Cristo</book>`;
    anydata adx = x;

    return (adxl, adx);
}

function testJSONAssignment() returns anydata {
    json j = {name: "apple", color: "red", price: 40};
    anydata adj = j;
    return adj;
}

function testTableAssignment() returns anydata {
    table<Employee> t = table {
        { primarykey id, name, salary },
        [
          { 1, "Mary", 300.5 },
          { 2, "John", 200.5 },
          { 3, "Jim", 330.5 }
        ]
    };
    anydata adt = t;
    return adt;
}

function testMapAssignment() {
    anydata ad;

    map<int> mi;
    ad = mi;

    map<float> mf;
    ad = mf;

    map<boolean> mb;
    ad = mb;

    map<byte> mby;
    ad = mby;

    map<string> ms;
    ad = ms;

    map<json> mj;
    ad = mj;

    map<xml> mx;
    ad = mx;

    map<Foo> mr;
    ad = mr;

    map<ClosedFoo> mcr;
    ad = mcr;

    map<table> mt;
    ad = mt;

    map<map<anydata>> mmad;
    ad = mmad;

    map<anydata[]> madr;
    ad = madr;

    map<DataType> mu;
    ad = mu;

    map<((DataType, string), int, float)> mtup;
    ad = mtup;

    map<()> mnil;
    ad = mnil;
}

function testConstrainedMaps() returns map<anydata> {
    byte b = 10;
    Foo foo = {a: 15};
    json j = {name: "apple", color: "red", price: 40};
    xml x = xml `<book>The Lost World</book>`;
    table<Employee> t = table {
            { primarykey id, name, salary },
            [ { 1, "Mary",  300.5 },
              { 2, "John",  200.5 },
              { 3, "Jim", 330.5 }
            ]
        };
    map<string> smap;

    smap["foo"] = "foo";
    smap["bar"] = "bar";

    map<anydata> adm;
    adm["byte"] = b;
    adm["json"] = j;
    adm["record"] = foo;
    adm["xml"] = x;
    adm["string"] = "Hello World";
    adm["int"] = 1234;
    adm["float"] = 23.45;
    adm["boolean"] = true;
    adm["map"] = smap;
    adm["table"] = t;
    adm["nil"] = ();

    return adm;
}

function testArrayAssignment() {
    anydata ad;

    int[] ai = [10, 20, 30];
    ad = ai;

    float[] af = [1.2, 2.3, 3.4];
    ad = af;

    boolean[] ab = [true, false, true];
    ad = ab;

    byte[] aby = [1, 2, 3];
    ad = aby;

    string[] ast = ["foo", "bar"];
    ad = ast;

    json[] aj = ["foo", "bar"];
    ad = aj;

    xml[] ax = [xml `<book>The Lost World</book>`];
    ad = ax;

    Foo[] ar = [{a:10}, {a:20}];
    ad = ar;

    ClosedFoo[] acr = [{ca:10}, {ca:20}];
    ad = acr;

    table<Employee> t = table {
                { primarykey id, name, salary },
                [ { 1, "Mary",  300.5 },
                  { 2, "John",  200.5 },
                  { 3, "Jim", 330.5 }
                ]
            };
    table[] at = [t];
    ad = at;

    map<anydata> m;
    map<anydata>[] amad = [m];
    ad = amad;

    anydata[] aad = [];
    ad = aad;

    anydata[][] a2ad = [];
    ad = a2ad;

    ()[] an = [];
    ad = an;
}

function testAnydataArray() returns anydata[] {
    Foo foo = {a:15};
    json j = {name: "apple", color: "red", price: 40};
    xml x = xml `<book>The Lost World</book>`;
    byte b = 10;
    anydata[] ad = [1234, 23.45, true, "Hello World!", b, foo, j, x];
    return ad;
}

type ValueType int|float|string|boolean|byte;
type DataType ValueType|table|json|xml|ClosedFoo|Foo|map<anydata>|anydata[]|();

function testUnionAssignment() returns anydata[] {
    anydata[] rets = [];
    int i = 0;

    ValueType vt = "hello world!";
    rets[i] = vt;
    i += 1;

    vt = 123;
    rets[i] = vt;
    i += 1;

    vt = 23.45;
    rets[i] = vt;
    i += 1;

    vt = true;
    rets[i] = vt;
    i += 1;

    byte b = 255;
    vt = b;
    rets[i] = vt;
    i += 1;

    return rets;
}

function testUnionAssignment2() returns anydata[] {
    anydata[] rets = [];
    int i = 0;

    DataType dt = "hello world!";
    rets[i] = dt;
    i += 1;

    table<Employee> t = table {
                { primarykey id, name, salary },
                [ { 1, "Mary",  300.5 },
                  { 2, "John",  200.5 },
                  { 3, "Jim", 330.5 }
                ]
            };
    dt = t;
    rets[i] = dt;
    i += 1;

    json j = {name: "apple", color: "red", price: 40};
    dt = j;
    rets[i] = dt;
    i += 1;

    dt = xml `<book>The Lost World</book>`;
    rets[i] = dt;
    i += 1;

    Foo foo = {a:15};
    dt = foo;
    rets[i] = dt;
    i += 1;

    ClosedFoo cfoo = {ca:15};
    dt = cfoo;
    rets[i] = dt;
    i += 1;

    map<anydata> m;
    m["foo"] = foo;
    dt = m;
    rets[i] = dt;
    i += 1;

    anydata[] adr = [];
    adr[0] = "hello world!";
    dt = adr;
    rets[i] = dt;
    i += 1;

    return rets;
}

function testTupleAssignment() returns anydata[] {
    anydata[] rets = [];
    int i = 0;

    byte b = 255;
    (int, float, boolean, string, byte) vt = (123, 23.45, true, "hello world!", b);
    rets[i] = vt;
    i += 1;

    json j = { name: "apple", color: "red", price: 40 };
    xml x = xml `<book>The Lost World</book>`;
    (json, xml) jxt = (j, x);
    rets[i] = jxt;
    i += 1;

    DataType[] dt = [j, x];
    (DataType[], string) ct = (dt, "hello world!");
    rets[i] = ct;
    i += 1;

    ((DataType[], string), int, float) nt = (ct, 123, 23.45);
    rets[i] = nt;

    return rets;
}

function testNilAssignment() returns anydata {
    anydata ad = ();
    return ad;
}

// TODO: do the following tests for ternary operator as well

function testAnydataToValueTypes() returns (int, float, boolean, string) {
    anydata ad = 33;
    int i;
    if (ad is int) {
        i = ad;
    }

    ad = 23.45;
    float f;
    if (ad is float) {
        f = ad;
    }

    ad = true;
    boolean bool;
    if (ad is boolean) {
        bool = ad;
    }

    ad = "Hello World!";
    string s;
    if (ad is string) {
        s = ad;
    }

    return (i, f, bool, s);
}

function testAnydataToJson() returns json {
    json j = { name: "apple", color: "red", price: 40 };
    anydata adJ = j;
    json convertedJ;
    if (adJ is json) {
        convertedJ = adJ;
    }
    return convertedJ;
}

function testAnydataToXml() returns xml {
    xml x = xml `<book>The Lost World</book>`;
    anydata adx = x;
    xml convertedX;
    if (adx is xml) {
        convertedX = adx;
    }
    return convertedX;
}

function testAnydataToRecord() returns (Foo, ClosedFoo) {
    Foo foo = {a: 15};
    anydata adr = foo;
    Foo convertedFoo;
    if (adr is Foo) {
        convertedFoo = adr;
    }

    ClosedFoo cFoo = {ca: 20};
    adr = cFoo;
    ClosedFoo convertedCFoo;
    if (adr is ClosedFoo) {
        convertedCFoo = adr;
    }
    return (convertedFoo, convertedCFoo);
}

function testAnydataToMap() {
    anydata ad;

    map<int> mi;
    ad = mi;
    map<int> convertedMi;
    if (ad is map<int>) {
        convertedMi = ad;
    }

    map<float> mf;
    ad = mf;
    map<float> convertedMf;
    if (ad is map<float>) {
        convertedMf = ad;
    }

    map<boolean> mb;
    ad = mb;
    map<boolean> convertedMb;
    if (ad is map<boolean>) {
        convertedMb = ad;
    }

    map<byte> mby;
    ad = mby;
    map<byte> convertedMby;
    if (ad is map<byte>) {
        convertedMby = ad;
    }

    map<string> ms;
    ad = ms;
    map<string> convertedMs;
    if (ad is map<string>) {
        convertedMs = ad;
    }

    map<json> mj;
    ad = mj;
    map<json> convertedMj;
    if (ad is map<json>) {
        convertedMj = ad;
    }

    map<xml> mx;
    ad = mx;
    map<xml> convertedMx;
    if (ad is map<xml>) {
        convertedMx = ad;
    }

    map<Foo> mr;
    ad = mr;
    map<Foo> convertedMfoo;
    if (ad is map<Foo>) {
        convertedMfoo = ad;
    }

    map<ClosedFoo> mcr;
    ad = mcr;
    map<ClosedFoo> convertedMCfoo;
    if (ad is map<ClosedFoo>) {
        convertedMCfoo = ad;
    }

    map<table> mt;
    ad = mt;
    map<table> convertedMt;
    if (ad is map<table>) {
        convertedMt = ad;
    }

    map<map<anydata>> mmad;
    ad = mmad;
    map<map<anydata>> convertedMmad;
    if (ad is map<map<anydata>>) {
        convertedMmad = ad;
    }

    map<anydata[]> madr;
    ad = madr;
    map<anydata[]> convertedMadr;
    if (ad is map<anydata[]>) {
        convertedMadr = ad;
    }

    map<DataType> mu;
    ad = mu;
    map<DataType> convertedMu;
    if (ad is map<DataType>) {
        convertedMu = ad;
    }

    map<((DataType, string), int, float)> mtup;
    ad = mtup;
    map<((DataType, string), int, float)> convertedMtup;
    if (ad is map<((DataType, string), int, float)>) {
        convertedMtup = ad;
    }

    map<()> mnil;
    ad = mnil;
    map<()> convertedMnil;
    if (ad is map<()>) {
        convertedMnil = ad;
    }
}

function testAnydataToTable() returns table<Employee> {
    table<Employee> t = table {
                { primarykey id, name, salary },
                [ { 1, "Mary",  300.5 },
                  { 2, "John",  200.5 },
                  { 3, "Jim", 330.5 }
                ]
            };
    anydata ad = t;
    table<Employee> convertedT;
    if (ad is table<Employee>) {
        convertedT = ad;
    }
    return convertedT;
}

function testAnydataToUnion() returns ValueType[] {
    anydata ad = 10;
    ValueType[] vt = [];
    int i = 0;

    if (ad is ValueType) {
        vt[i] = ad;
        i += 1;
    }

    ad = 23.45;
    if (ad is ValueType) {
        vt[i] = ad;
        i += 1;
    }

    ad = "hello world!";
    if (ad is ValueType) {
        vt[i] = ad;
        i += 1;
    }

    ad = true;
    if (ad is ValueType) {
        vt[i] = ad;
        i += 1;
    }

    byte b = 255;
    ad = b;
    if (ad is ValueType) {
        vt[i] = ad;
        i += 1;
    }

    return vt;
}

function testAnydataToUnion2() returns DataType[] {
    anydata ad;
    DataType[] dt = [];
    int i = 0;

    json j = { name: "apple", color: "red", price: 40 };
    ad = j;
    if (ad is DataType) {
        dt[i] = ad;
        i += 1;
    }

    xml x = xml `<book>The Lost World</book>`;
    ad = x;
    if (ad is DataType) {
        dt[i] = ad;
        i += 1;
    }

    table<Employee> t = table {
                    { primarykey id, name, salary },
                    [ { 1, "Mary",  300.5 },
                      { 2, "John",  200.5 },
                      { 3, "Jim", 330.5 }
                    ]
                };
    ad = t;
    if (ad is DataType) {
        dt[i] = ad;
        i += 1;
    }

    Foo foo = {a: 15};
    ad = foo;
    if (ad is DataType) {
        dt[i] = ad;
        i += 1;
    }

    ClosedFoo cfoo = {ca: 15};
    ad = cfoo;
    if (ad is DataType) {
        dt[i] = ad;
        i += 1;
    }

    map<anydata> m;
    m["foo"] = foo;
    ad = m;
    if (ad is DataType) {
        dt[i] = ad;
        i += 1;
    }

    anydata[] adr = [];
    adr[0] = foo;
    ad = adr;
    if (ad is DataType) {
        dt[i] = ad;
        i += 1;
    }

    return dt;
}

function testAnydataToTuple() returns (int, float, boolean, string, byte)? {
    anydata ad;

    byte b = 255;
    (int, float, boolean, string, byte) vt = (123, 23.45, true, "hello world!", b);
    ad = vt;
    if (ad is (int, float, boolean, string, byte)) {
        return ad;
    }

    return ();
}

function testAnydataToTuple2() returns (json, xml)? {
    anydata ad;

    json j = { name: "apple", color: "red", price: 40 };
    xml x = xml `<book>The Lost World</book>`;
    (json, xml) jxt = (j, x);
    ad = jxt;

    if (ad is (json, xml)) {
        return ad;
    }

    return ();
}

function testAnydataToTuple3() returns ((DataType[], string), int, float)? {
    anydata ad;

    json j = { name: "apple", color: "red", price: 40 };
    xml x = xml `<book>The Lost World</book>`;
    DataType[] dt = [j, x];
    (DataType[], string) ct = (dt, "hello world!");
    ((DataType[], string), int, float) nt = (ct, 123, 23.45);
    ad = nt;

    if (ad is ((DataType[], string), int, float)) {
        return ad;
    }

    return ();
}

function testAnydataToNil() returns int? {
    () nil = ();
    anydata ad = nil;

    if (ad is ()) {
        return ad;
    }

    return -1;
}
