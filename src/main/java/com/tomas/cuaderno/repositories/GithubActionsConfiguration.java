package com.tomas.cuaderno.repositories;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
@EnableConfigurationProperties(RepositoryStatusProperties.class)
public class GithubActionsConfiguration {
    @Bean
    GithubActionsClient githubActionsClient(RestClient.Builder builder, RepositoryStatusProperties properties) {
        var timeout = properties.getGithub().getTimeout();
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);

        RestClient.Builder configured = builder
                .baseUrl(properties.getGithub().getApiBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.USER_AGENT, "Cuaderno-Repository-Status")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (StringUtils.hasText(properties.getGithub().getApiToken())) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getGithub().getApiToken());
        }

        var client = configured.build();
        var proxyFactory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build();
        return proxyFactory.createClient(GithubActionsClient.class);
    }
}
