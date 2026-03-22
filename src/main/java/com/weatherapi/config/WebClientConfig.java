package com.weatherapi.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    /**
     * WebClient pre-configured for Visual Crossing with three timeout layers.
     *
     * Without explicit timeouts a slow or hung upstream holds a Tomcat thread
     * open until the OS TCP timeout fires — typically 2+ minutes. Under load,
     * this exhausts the thread pool and cascades to all endpoints.
     *
     * Three layers, three failure modes:
     *
     *   CONNECT_TIMEOUT_MILLIS (5 s)
     *     Fails fast when Visual Crossing is unreachable or DNS is broken.
     *     Set at the Netty channel level — fires before any data is sent.
     *
     *   responseTimeout (5 s)
     *     Total wall-clock time from sending the request to receiving the last
     *     byte of the response body. Covers slow Visual Crossing responses end-to-end.
     *
     *   ReadTimeoutHandler (10 s)
     *     Per-read inactivity window inside an established connection. Catches
     *     "slow drip" responses where bytes arrive just fast enough to reset
     *     the response timeout but the full body never completes.
     *
     * responseTimeout < ReadTimeoutHandler intentionally: the total-time limit fires
     * before the per-read inactivity limit in normal slow-response scenarios.
     */
    @Bean
    public WebClient visualCrossingWebClient(
            WebClient.Builder builder,
            @Value("${weather.api.base-url}") String baseUrl) {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofSeconds(5))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))
                );

        return builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
