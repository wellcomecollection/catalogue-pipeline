import boto3
import httpx


class SierraClient:
    def __init__(self, *, api_url, oauth_key, oauth_secret):
        self.oauth_key = oauth_key
        self.oauth_secret = oauth_secret

        self.client = httpx.Client(base_url=api_url)

        self.responses = []
        self._refresh_auth_token()

    def _refresh_auth_token(self):
        # Get an access token
        # https://sandbox.iii.com/docs/Content/zReference/authClient.htm
        resp = self.client.post("/token", auth=(self.oauth_key, self.oauth_secret))

        try:
            access_token = resp.json()["access_token"]
        except KeyError:
            print(resp)
            print(resp.json())
            raise

        self.client.headers = {
            "Authorization": f"Bearer {access_token}",
            "Accept": "application/json",
            "Connection": "close",
        }

    def _get_objects_from_id(self, path, id, params):
        id_param = {"id": f"[{id},]"}
        merged_params = {**id_param, **params}
        try:
            json_response = self.client.get(path, params=merged_params).json()

            self._current_response = json_response

            return self._current_response
        except httpx.HTTPError as err:

            # When requesting a set of objects that is empty
            # the API will return a 404, so substitute for an
            # empty list.
            if err.response.status_code == 404:
                return []
            else:
                raise

    def get_objects(self, path, params=None):
        if params is None:
            params = {}

        def _get(id):
            return self._get_objects_from_id(path=path, id=id, params=params)

        class ObjectIterable(object):
            def __init__(_self):
                _self.objs = _get(0)

            def __len__(_self):
                return self._current_response["total"]

            def __iter__(_self):
                while True:
                    try:
                        yield from _self.objs["entries"]
                        last_id = int(_self.objs["entries"][-1]["id"]) + 1
                        _self.objs = _get(last_id)
                    except KeyError:
                        break

            def next(_self):
                return next(_self._gen)

        o = ObjectIterable()

        return o


def catalogue_client():
    sess = boto3.Session()

    secrets_client = sess.client("secretsmanager")

    sierra_api_root = "https://libsys.wellcomelibrary.org/iii/sierra-api/v6"
    sierra_client_key = secrets_client.get_secret_value(
        SecretId="sierra_adapter/sierra_api_key"
    )["SecretString"]
    sierra_client_secret = secrets_client.get_secret_value(
        SecretId="sierra_adapter/sierra_api_client_secret"
    )["SecretString"]

    return SierraClient(
        api_url=sierra_api_root,
        oauth_key=sierra_client_key,
        oauth_secret=sierra_client_secret,
    )
