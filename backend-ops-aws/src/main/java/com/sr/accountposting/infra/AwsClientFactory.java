package com.sr.accountposting.infra;

import com.sr.accountposting.util.AppConfig;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;

public class AwsClientFactory {

    private static final Region REGION = Region.of(AppConfig.AWS_REGION);

    private static final DynamoDbClient DYNAMO_CLIENT;
    private static final DynamoDbEnhancedClient ENHANCED_CLIENT;
    private static final SqsClient SQS_CLIENT;

    static {
        URI endpoint = endpointOverride();
        AwsCredentialsProvider creds = credentialsProvider();

        DynamoDbClientBuilder dynamo = DynamoDbClient.builder()
                .region(REGION)
                .credentialsProvider(creds)
                .httpClient(UrlConnectionHttpClient.create());
        if (endpoint != null) dynamo.endpointOverride(endpoint);
        DYNAMO_CLIENT = dynamo.build();

        ENHANCED_CLIENT = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DYNAMO_CLIENT)
                .build();

        SqsClientBuilder sqs = SqsClient.builder()
                .region(REGION)
                .credentialsProvider(creds)
                .httpClient(UrlConnectionHttpClient.create());
        if (endpoint != null) sqs.endpointOverride(endpoint);
        SQS_CLIENT = sqs.build();
    }

    private AwsClientFactory() {
    }

    public static DynamoDbClient dynamoDbClient() {
        return DYNAMO_CLIENT;
    }

    public static DynamoDbEnhancedClient enhancedClient() {
        return ENHANCED_CLIENT;
    }

    public static SqsClient sqsClient() {
        return SQS_CLIENT;
    }

    private static URI endpointOverride() {
        String url = System.getenv("AWS_ENDPOINT_URL");
        if (url == null) url = System.getProperty("aws.endpointUrl");
        return url != null ? URI.create(url) : null;
    }

    private static AwsCredentialsProvider credentialsProvider() {
        String key = System.getenv("AWS_ACCESS_KEY_ID");
        if (key == null) key = System.getProperty("aws.accessKeyId");
        if (key != null) {
            String secret = System.getenv("AWS_SECRET_ACCESS_KEY");
            if (secret == null) secret = System.getProperty("aws.secretAccessKey");
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(key, secret != null ? secret : ""));
        }
        return DefaultCredentialsProvider.create();
    }
}
