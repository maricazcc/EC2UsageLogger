package ec2logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

public class EC2UsageLogger implements RequestHandler<Object, String> {

    private final Ec2Client ec2 = Ec2Client.create();
    private final S3Client s3 = S3Client.create();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BUCKET = "ec2-usage-logs-zocco";

    @Override
    public String handleRequest(Object input, Context context) {

        List<Map<String, Object>> logs = new ArrayList<>();

        DescribeInstancesResponse response = ec2.describeInstances();

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {

                String instanceType = instance.instanceType().toString();

                //SOLO t2.micro (policy)
                if (!instanceType.equalsIgnoreCase("t2.micro")) {
                    continue;
                }

                Map<String, Object> record = new HashMap<>();
                record.put("instanceId", instance.instanceId());
                record.put("state", instance.state().nameAsString());
                record.put("instanceType", instanceType);
                record.put("timestamp", Instant.now().toString());

                logs.add(record);
            }
        }

        try {

            String json = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(logs);

            String fileName = "usage-" + System.currentTimeMillis() + ".json";

            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(BUCKET)
                            .key(fileName)
                            .build(),
                    RequestBody.fromString(json)
            );

            context.getLogger().log("Log salvato: " + fileName + "\n");

        } catch (Exception e) {
            context.getLogger().log("Errore: " + e.getMessage() + "\n");
        }

        return "Logging completato";
    }
}