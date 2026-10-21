import {
    SecretsManagerClient,
    GetSecretValueCommand,
} from "@aws-sdk/client-secrets-manager";
import { Client } from "@elastic/elasticsearch";

const secretsClient = new SecretsManagerClient({});
const getSecret = async (id: string): Promise<string | undefined> => {
    try {
        const result = await secretsClient.send(
            new GetSecretValueCommand({ SecretId: id })
        );
        return result.SecretString;
    } catch (e) {
        console.error(`Error fetching secret '${id}'`, e);
        return undefined;
    }
};


export const getPipelineClient = async (pipelineDate: string): Promise<Client> => {
    const secretPrefix = `elasticsearch/pipeline_storage_${pipelineDate}`
    // Serverless pipelines have no read_only user and listen on 443 only
    const [host, port, apiKey] = await Promise.all([
        getSecret(`${secretPrefix}/public_host`),
        getSecret(`${secretPrefix}/port`),
        getSecret(`${secretPrefix}/read_only/api_key`),
    ])
    return new Client({
        node: `https://${host}:${port}`,
        auth: { apiKey }
    });
}
