package com.ferraborghini.httpclient.utils;

import org.apache.http.HeaderElement;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;


public class HttpWrapper {
    private CloseableHttpClient client;


    public HttpWrapper(){
        init();
    }

    public void init(){
        ConnectionSocketFactory plainSocketFactory = PlainConnectionSocketFactory.getSocketFactory();
        LayeredConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactory.getSocketFactory();
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create().register("http", plainSocketFactory)
                .register("https", sslSocketFactory).build();
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(registry);
        manager.setMaxTotal(100); // 线程池最大数量
        manager.setDefaultMaxPerRoute(10); // 相同路由请求的最大连接数量
        HttpRequestRetryHandler handler = new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException e, int i, HttpContext httpContext) {
                if (i > 3) {
                    //重试超过3次,放弃请求
                    System.out.println("retry has more than 3 time, give up request");
                    return false;
                }
                if (e instanceof NoHttpResponseException) {
                    //服务器没有响应,可能是服务器断开了连接,应该重试
                    System.out.println("receive no response from server, retry");
                    return true;
                }
                if (e instanceof SSLHandshakeException) {
                    // SSL握手异常
                    System.out.println("SSL hand shake exception");
                    return false;
                }
                if (e instanceof InterruptedIOException) {
                    //超时
                    System.out.println("InterruptedIOException");
                    return false;
                }
                if (e instanceof UnknownHostException) {
                    // 服务器不可达
                    System.out.println("server host unknown");
                    return false;
                }
                if (e instanceof ConnectTimeoutException) {
                    // 连接超时
                    System.out.println("Connection Time out");
                    return false;
                }
                if (e instanceof SSLException) {
                    System.out.println("SSLException");
                    return false;
                }

                HttpClientContext context = HttpClientContext.adapt(httpContext);
                HttpRequest request = context.getRequest();
                if (!(request instanceof HttpEntityEnclosingRequest)) {
                    //如果请求不是关闭连接的请求
                    return true;
                }
                return false;
            }
        };

        HttpClientBuilder clientBuilder = HttpClients.custom().setConnectionManager(manager).setRetryHandler(handler);
        /**
         * 如果想自定义keep-alive逻辑
         */
        clientBuilder.setKeepAliveStrategy((httpResponse, httpContext) -> {
            BasicHeaderElementIterator it = new BasicHeaderElementIterator(httpResponse.headerIterator("Keep-Alive"));
            while(true) {
                String param;
                String value;
                do {
                    do {
                        if (!it.hasNext()) {
                            return -1L;
                        }

                        HeaderElement he = it.nextElement();
                        param = he.getName();
                        value = he.getValue();
                    } while(value == null);
                } while(!param.equalsIgnoreCase("timeout"));

                try {
                    return Long.parseLong(value) * 1000L;
                } catch (NumberFormatException var8) {
                    ;
                }
            }
        });
        this.client = clientBuilder.build();
    }
}
