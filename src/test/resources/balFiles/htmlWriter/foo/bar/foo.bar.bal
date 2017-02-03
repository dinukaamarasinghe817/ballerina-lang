package foo.bar;

import ballerina.lang.system;
import ballerina.lang.message;
import ballerina.lang.json;

@Description("Add HTTP header to the message")
@Param("m: Incoming message")
@Param("key: HTTP header key")
@Param("value: HTTP header value")
public function addHeader (message m, string key, string value) {
    system:println("invoked");
}

@Description("Get HTTP header from the message")
@Param("m: Incoming message")
@Param("key: HTTP header key")
@Return("value: HTTP header value")
public function getHeader (message m, string key) (string value) {
    system:println("invoked");
    return value;
}

@Description("Test connector")
@Param("consumerKey: consumer key")
@Param("consumerSecret: consumer secret")
@Param("accessToken: access token")
@Param("accessTokenSecret: access token secret")
connector TestConnector(string consumerKey, string consumerSecret, string accessToken, string accessTokenSecret) {
    @Description("test connector action")
    @Param("t: a string argument")
    @Param("msg: a string message")
    @Return("response: response object")
    action testAction1(string t, string msg) (message response) {
        message request;
        response = request;
        return response;
    }

    @Description("test connector action2")
    @Param("args: arguments")
    @Return("response object")
    action testAction2(string t, string msg) (message) {
        message request;
        message response;
        return response;
    }
}
