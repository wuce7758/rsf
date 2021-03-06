/*
 * Copyright 2008-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.hasor.rsf.protocol.hprose;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import net.hasor.rsf.RsfContext;
import net.hasor.rsf.domain.ProtocolStatus;
import net.hasor.rsf.domain.RequestInfo;
import net.hasor.rsf.domain.ResponseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
/**
 * Hprose
 * @version : 2017年1月26日
 * @author 赵永春(zyc@hasor.net)
 */
@ChannelHandler.Sharable
public class HproseHttpCoder extends ChannelDuplexHandler {
    protected Logger logger = LoggerFactory.getLogger(getClass());
    private RsfContext rsfContext;
    //
    public HproseHttpCoder(RsfContext rsfContext) {
        this.rsfContext = rsfContext;
    }
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ResponseInfo) {
            ResponseInfo response = (ResponseInfo) msg;
            long requestID = response.getRequestID();
            if (response.getStatus() == ProtocolStatus.Accept) {
                return;
            }
            //
            ByteBuf result = HproseUtils.doResult(requestID, response);
            FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, result);
            httpResponse.headers().set(CONTENT_TYPE, "application/hprose");
            httpResponse.headers().set(CONTENT_LENGTH, result.readableBytes());
            super.write(ctx, httpResponse, promise);
            return;
        }
        super.write(ctx, msg, promise);
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpContent) {
            long requestID = 12345L;// todo 确定新的 requestID
            HttpContent http = (HttpContent) msg;
            RequestInfo info = HproseUtils.doCall(this.rsfContext, requestID, http.content());
            super.channelRead(ctx, info);
            return;
        }
        super.channelRead(ctx, msg);
    }
}