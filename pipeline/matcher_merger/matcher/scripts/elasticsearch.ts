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
    // TODO use an API key
    const [host, username, password] = await Promise.all([
        getSecret(`${secretPrefix}/public_host`),
        getSecret(`${secretPrefix}/read_only/es_username`),
        getSecret(`${secretPrefix}/read_only/es_password`),
    ])
    return new Client({
        node: `https://${host}:9243`,
        auth: { username, password }
    });
}
