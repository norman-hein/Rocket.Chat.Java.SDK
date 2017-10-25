package com.rocketchat.core;

import com.rocketchat.common.RocketChatAuthException;
import com.rocketchat.common.RocketChatException;
import com.rocketchat.common.RocketChatInvalidResponseException;
import com.rocketchat.common.data.CommonJsonAdapterFactory;
import com.rocketchat.common.data.TimestampAdapter;
import com.rocketchat.common.data.model.BaseRoom;
import com.rocketchat.common.listener.PaginatedCallback;
import com.rocketchat.common.utils.NoopLogger;
import com.rocketchat.common.utils.Sort;
import com.rocketchat.core.callback.LoginCallback;
import com.rocketchat.core.model.JsonAdapterFactory;
import com.rocketchat.core.model.Message;
import com.rocketchat.core.model.Token;
import com.rocketchat.core.model.attachment.Attachment;
import com.rocketchat.core.provider.TokenProvider;
import com.squareup.moshi.Moshi;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import io.fabric8.mockwebserver.DefaultMockServer;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class RestImplTest {

    private RestImpl rest;
    private DefaultMockServer mockServer;
    @Mock private TokenProvider tokenProvider;
    @Mock private LoginCallback loginCallback;
    @Mock private PaginatedCallback paginatedCallback;
    @Captor private ArgumentCaptor<Token> tokenCaptor;
    @Captor private ArgumentCaptor<List> listCaptor;
    @Captor private ArgumentCaptor<List<Message>> messagesCaptor;
    @Captor private ArgumentCaptor<RocketChatException> exceptionCaptor;

    @Before
    public void setup() {
        mockServer = new DefaultMockServer();
        mockServer.start();

        HttpUrl baseUrl = HttpUrl.parse(mockServer.url("/"));
        OkHttpClient client = new OkHttpClient();

        Moshi moshi = new Moshi.Builder()
                .add(new TimestampAdapter())
                .add(JsonAdapterFactory.create())
                .add(CommonJsonAdapterFactory.create())
                .build();

        rest = new RestImpl(client, moshi, baseUrl, tokenProvider, new NoopLogger());
    }

    //     _____ _____ _____ _   _ _____ _   _    _______ ______  _____ _______ _____
    //    / ____|_   _/ ____| \ | |_   _| \ | |  |__   __|  ____|/ ____|__   __/ ____|
    //   | (___   | || |  __|  \| | | | |  \| |     | |  | |__  | (___    | | | (___
    //    \___ \  | || | |_ | . ` | | | | . ` |     | |  |  __|  \___ \   | |  \___ \
    //    ____) |_| || |__| | |\  |_| |_| |\  |     | |  | |____ ____) |  | |  ____) |
    //   |_____/|_____\_____|_| \_|_____|_| \_|     |_|  |______|_____/   |_| |_____/
    //
    //

    @Test(expected = NullPointerException.class)
    public void testSigninShouldFailWithNullUsername() {
        rest.signin(null, "password", loginCallback);
    }

    @Test(expected = NullPointerException.class)
    public void testSigninShouldFailWithNullPassword() {
        rest.signin("username", null, loginCallback);
    }

    @Test(expected = NullPointerException.class)
    public void testSigninShouldFailWithNullCallback() {
        rest.signin("username", "password", null);
    }

    @Test
    public void tesSigninShouldBeSuccessful() {
        mockServer.expect()
                .post()
                .withPath("/api/v1/login")
                .andReturn(200, "{\"status\": \"success\",\"data\": {\"authToken\": \"token\",\"userId\": \"userid\"}}")
                .once();

        rest.signin("user", "password", loginCallback);

        verify(loginCallback, timeout(100).only())
                .onLoginSuccess(tokenCaptor.capture());

        Token token = tokenCaptor.getValue();
        assertThat(token.getUserId(), is(equalTo("userid")));
        assertThat(token.getAuthToken(), is(equalTo("token")));
        assertThat(token.getExpiry(), is(nullValue()));
    }

    @Test
    public void testSigninShouldFailOnInvalidJson() {
        mockServer.expect()
                .post()
                .withPath("/api/v1/login")
                .andReturn(200, "NOT A JSON")
                .once();

        rest.signin("user", "password", loginCallback);
        verify(loginCallback, timeout(100).only())
                .onError(exceptionCaptor.capture());

        RocketChatException exception = exceptionCaptor.getValue();
        assertThat(exception, is(instanceOf(RocketChatInvalidResponseException.class)));
        assertThat(exception.getMessage(), is(equalTo("A JSONObject text must begin with '{' at character 1")));
        assertThat(exception.getCause(), is(instanceOf(JSONException.class)));
    }

    @Test
    public void testSigninShouldFailWithAuthExceptionOn401() {
        mockServer.expect()
                .post()
                .withPath("/api/v1/login")
                .andReturn(401, "{\"status\": \"error\",\"message\": \"Unauthorized\"}")
                .once();

        rest.signin("user", "password", loginCallback);

        verify(loginCallback, timeout(200).only())
                .onError(exceptionCaptor.capture());
        RocketChatException exception = exceptionCaptor.getValue();
        assertThat(exception, is(instanceOf(RocketChatAuthException.class)));
        assertThat(exception.getMessage(), is(equalTo("Unauthorized")));
    }

    @Test
    public void testSigninShouldFailIfNot2xx() {
        rest.signin("user", "password", loginCallback);

        verify(loginCallback, timeout(200).only())
                .onError(exceptionCaptor.capture());
        RocketChatException exception = exceptionCaptor.getValue();
        assertThat(exception, is(instanceOf(RocketChatException.class)));

    }

    //     _____ ______ _______     _____   ____   ____  __  __        ______ _____ _      ______  _____   _______ ______  _____ _______ _____
    //    / ____|  ____|__   __|   |  __ \ / __ \ / __ \|  \/  |      |  ____|_   _| |    |  ____|/ ____| |__   __|  ____|/ ____|__   __/ ____|
    //   | |  __| |__     | |______| |__) | |  | | |  | | \  / |______| |__    | | | |    | |__  | (___      | |  | |__  | (___    | | | (___
    //   | | |_ |  __|    | |______|  _  /| |  | | |  | | |\/| |______|  __|   | | | |    |  __|  \___ \     | |  |  __|  \___ \   | |  \___ \
    //   | |__| | |____   | |      | | \ \| |__| | |__| | |  | |      | |     _| |_| |____| |____ ____) |    | |  | |____ ____) |  | |  ____) |
    //    \_____|______|  |_|      |_|  \_\\____/ \____/|_|  |_|      |_|    |_____|______|______|_____/     |_|  |______|_____/   |_| |_____/
    //
    //

    @Test(expected = NullPointerException.class)
    public void testGetRoomFilesShouldFailWithNullRoomId() {
        rest.getRoomFiles(null, BaseRoom.RoomType.PUBLIC, 0, Attachment.SortBy.UPLOADED_DATE, Sort.DESC, paginatedCallback);
    }

    @Test(expected = NullPointerException.class)
    public void testGetRoomFilesShouldFailWithNullRoomType() {
        rest.getRoomFiles("roomId", null, 0, Attachment.SortBy.UPLOADED_DATE, Sort.DESC, paginatedCallback);
    }

    @Test(expected = NullPointerException.class)
    public void testGetRoomFilesShouldFailWithNullSortBy() {
        rest.getRoomFiles("roomId", BaseRoom.RoomType.PUBLIC, 0, null, Sort.DESC, paginatedCallback);
    }

    @Test(expected = NullPointerException.class)
    public void testGetRoomFilesShouldFailWithNullSort() {
        rest.getRoomFiles("roomId", BaseRoom.RoomType.PUBLIC, 0, Attachment.SortBy.UPLOADED_DATE, null, paginatedCallback);
    }

    @Test(expected = NullPointerException.class)
    public void testGetRoomFilesShouldFailWithNullCallback() {
        rest.getRoomFiles("roomId", BaseRoom.RoomType.PUBLIC, 0, Attachment.SortBy.UPLOADED_DATE, Sort.DESC, null);
    }

    @Test
    public void testGetRoomFilesShouldFailOnInvalidJson() {
        mockServer.expect()
                .get()
                .withPath("/api/v1/channels.files")
                .andReturn(200, "NOT A JSON")
                .once();

        rest.getRoomFiles("general", BaseRoom.RoomType.PUBLIC, 0, Attachment.SortBy.UPLOADED_DATE, Sort.DESC, paginatedCallback);
        verify(paginatedCallback, timeout(100).only())
                .onError(exceptionCaptor.capture());

        RocketChatException exception = exceptionCaptor.getValue();
        assertThat(exception.getMessage(), is(equalTo("A JSONObject text must begin with '{' at character 0")));
        assertThat(exception.getCause(), is(instanceOf(JSONException.class)));
    }

    @Test
    //TODO Needs to know why it is failing.
    public void testGetRoomFilesShouldBeSuccessful() {
        mockServer.expect()
                .post()
                .withPath("/api/v1/channels.files")
                .andReturn(200, "\"total\":5000," +
                        "   \"offset\":0," +
                        "   \"success\":true," +
                        "   \"count\":1," +
                        "   \"files\":[" +
                        "      {" +
                        "         \"extension\":\"txt\"," +
                        "         \"description\":\"\"," +
                        "         \"store\":\"GoogleCloudStorage:Uploads\"," +
                        "         \"type\":\"text/plain\"," +
                        "         \"rid\":\"GENERAL\"," +
                        "         \"userId\":\"mTYz5v78fEETuyvxH\"," +
                        "         \"url\":\"/ufs/GoogleCloudStorage:Uploads/B5HXEJQvoqXjfMyKD/%E6%96%B0%E5%BB%BA%E6%96%87%E6%9C%AC%E6%96%87%E6%A1%A3%20(2).txt\"," +
                        "         \"token\":\"C8BB59192B\"," +
                        "         \"path\":\"/ufs/GoogleCloudStorage:Uploads/B5HXEJQvoqXjfMyKD/%E6%96%B0%E5%BB%BA%E6%96%87%E6%9C%AC%E6%96%87%E6%A1%A3%20(2).txt\"," +
                        "         \"GoogleStorage\":{" +
                        "            \"path\":\"eoRXMCHBbQCdDnrke/uploads/GENERAL/mTYz5v78fEETuyvxH/B5HXEJQvoqXjfMyKD\"" +
                        "         }," +
                        "         \"instanceId\":\"kPnqzFNvmxkMWdcKC\"," +
                        "         \"size\":469," +
                        "         \"name\":\"sample.txt\"," +
                        "         \"progress\":1," +
                        "         \"uploadedAt\":\"2017-10-23T05:13:44.875Z\"," +
                        "         \"uploading\":false," +
                        "         \"etag\":\"mXWYhuiWiCxXpDYdg\"," +
                        "         \"_id\":\"B5HXEJQvoqXjfMyKD\"," +
                        "         \"complete\":true," +
                        "         \"_updatedAt\":\"2017-10-23T05:13:43.220Z\"" +
                        "      }" +
                        "   ]" +
                        "}")
                .once();

        rest.getRoomFiles("general", BaseRoom.RoomType.PUBLIC, 0, Attachment.SortBy.UPLOADED_DATE, Sort.DESC, paginatedCallback);

        verify(paginatedCallback, timeout(100).only())
                .onSuccess(listCaptor.capture(), anyInt());

//        List attachmentList = listCaptor.getValue();
//        assertThat(attachmentList, is(notNullValue()));
    }

    //     _____ ______ _______     _____   ____   ____  __  __        _____ _____ _   _ _   _ ______ _____         __  __ ______  _____ _____         _____ ______  _____   _______ ______  _____ _______ _____
    //    / ____|  ____|__   __|   |  __ \ / __ \ / __ \|  \/  |      |  __ \_   _| \ | | \ | |  ____|  __ \       |  \/  |  ____|/ ____/ ____|  /\   / ____|  ____|/ ____| |__   __|  ____|/ ____|__   __/ ____|
    //   | |  __| |__     | |______| |__) | |  | | |  | | \  / |______| |__) || | |  \| |  \| | |__  | |  | |______| \  / | |__  | (___| (___   /  \ | |  __| |__  | (___      | |  | |__  | (___    | | | (___
    //   | | |_ |  __|    | |______|  _  /| |  | | |  | | |\/| |______|  ___/ | | | . ` | . ` |  __| | |  | |______| |\/| |  __|  \___ \\___ \ / /\ \| | |_ |  __|  \___ \     | |  |  __|  \___ \   | |  \___ \
    //   | |__| | |____   | |      | | \ \| |__| | |__| | |  | |      | |    _| |_| |\  | |\  | |____| |__| |      | |  | | |____ ____) |___) / ____ \ |__| | |____ ____) |    | |  | |____ ____) |  | |  ____) |
    //    \_____|______|  |_|      |_|  \_\\____/ \____/|_|  |_|      |_|   |_____|_| \_|_| \_|______|_____/       |_|  |_|______|_____/_____/_/    \_\_____|______|_____/     |_|  |______|_____/   |_| |_____/
    //
    //

    @Test(expected = NullPointerException.class)
    public void testGetRoomPinnedMessagesShouldFailWithNullRoomId() {
        rest.getRoomPinnedMessages(null, BaseRoom.RoomType.PUBLIC, 0, paginatedCallback);
    }

    @Test(expected = NullPointerException.class)
    public void testGetRoomPinnedMessagesShouldFailWithNullRoomType() {
        rest.getRoomPinnedMessages(null, null, 0, paginatedCallback);
    }

    @Test(expected = NullPointerException.class)
    public void testGetRoomPinnedMessagesShouldFailWithNullCallback() {
        rest.getRoomPinnedMessages(null, BaseRoom.RoomType.PUBLIC, 0, null);
    }

    public void testGetRoomPinnedMessagesShouldBeSuccessful() {
        mockServer.expect()
                .post()
                .withPath("/api/v1/channels.messages?roomId=GENERAL&offset=0&query={\"pinned\":true}")
                .andReturn(200, "{" +
                        "   \"total\":50," +
                        "   \"offset\":0," +
                        "   \"success\":true," +
                        "   \"count\":1," +
                        "   \"messages\":[" +
                        "      {" +
                        "         \"msg\":\"##滚滚滚\"," +
                        "         \"pinned\":true," +
                        "         \"channels\":[]," +
                        "         \"u\":{" +
                        "            \"name\":\"ugcode\"," +
                        "            \"_id\":\"aS66iLvbLN7LhFBJ5\"," +
                        "            \"username\":\"ugcode\"" +
                        "         }," +
                        "         \"translations\":{" +
                        "            \"en\":\"##roll roll roll\"" +
                        "         }," +
                        "         \"mentions\":[]," +
                        "         \"_id\":\"qQ3AaUlgt9RWeB9hYi\"," +
                        "         \"pinnedBy\":{" +
                        "            \"_id\":\"aS66iLvbLN7LhFBJ5\"," +
                        "            \"username\":\"ugcode\"" +
                        "         }," +
                        "         \"rid\":\"GENERAL\"," +
                        "         \"_updatedAt\":\"2017-10-21T02:33:44.737Z\"," +
                        "         \"ts\":\"2017-10-21T02:28:24.682Z\"," +
                        "         \"pinnedAt\":\"2017-10-21T02:33:44.737Z\"" +
                        "      }" +
                        "   ]" +
                        "}")
                .once();

        rest.getRoomPinnedMessages("general", BaseRoom.RoomType.PUBLIC, 0, paginatedCallback);

        verify(paginatedCallback, timeout(100).only())
                .onSuccess(messagesCaptor.capture(), anyInt());

        List<Message> messageList = messagesCaptor.getValue();
        assertThat(messageList, is(notNullValue()));
        assertThat(messageList.size(), is(equalTo(1)));
        Message message = messageList.get(0);
        assertThat(message.id(), is(equalTo("qQ3AaUlgt9RWeB9hYi")));
    }
}