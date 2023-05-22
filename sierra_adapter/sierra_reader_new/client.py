import datetime
import json
import time

import boto3
import httpx


class TokenExpiredError(Exception):
    pass


class SierraClient:
    def __init__(self, *, api_url, oauth_key, oauth_secret):
        self.oauth_key = oauth_key
        self.oauth_secret = oauth_secret

        self.client = httpx.Client(base_url=api_url)

        self.responses = []
        self._refresh_auth_token()

    def _refresh_auth_token(self):
        # Get an access token from Sierra.
        # See https://techdocs.iii.com/sierraapi/Content/zAuth/authClient.htm
        #
        # Our instance of Sierra creates access tokens that are valid for
        # an hour, so rather than requesting a new token on every run of
        # the Lambda, cache the token on the filesystem so it can be
        # reused between runs.
        try:
            with open("/tmp/access_token.json") as infile:
                data = json.load(infile)

            # Check if the cached token is expired.  We actually check
            # if it's good for another 5 minutes (the max runtime of
            # this Lambda), so we know the token is good for this entire
            # invocation.  If it's close to expire, we refresh it early
            # to avoid expiry mid-run.
            expiry_time = datetime.datetime.fromisoformat(data["expiry_time"])
            now = datetime.datetime.now() + datetime.timedelta(minutes=5)

            if expiry_time <= now:
                print(f"Access token is expired, skipping cached credentials… (expiry_time = {expiry_time}, now = {now})")
                raise TokenExpiredError

            print("Using cached access token credentials…")
            access_token = data["access_token"]
        except (FileNotFoundError, TokenExpiredError):
            print("Fetching new access token from Sierra…")

            # Get an access token
            # https://sandbox.iii.com/docs/Content/zReference/authClient.htm
            resp = self.client.post("/token", auth=(self.oauth_key, self.oauth_secret))

            try:
                access_token = resp.json()["access_token"]
            except KeyError:
                print(resp)
                print(resp.json())
                raise

            with open("/tmp/access_token.json", "w") as outfile:
                expires_in = datetime.timedelta(seconds=resp.json()["expires_in"])

                outfile.write(
                    json.dumps(
                        {
                            "access_token": access_token,
                            "expiry_time": (
                                datetime.datetime.now() + expires_in
                            ).isoformat(),
                        }
                    )
                )

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
            if hasattr(err, "response") and err.response.status_code == 404:
                return []

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
                        entries = _self.objs["entries"]

                        print(f"Got a batch of {len(entries)} records from Sierra…")
                        yield from entries

                        last_id = int(entries[-1]["id"]) + 1
                        _self.objs = _get(last_id)

                        time.sleep(1 / 3)
                    except KeyError:
                        break

            def next(_self):
                return next(_self._gen)

        o = ObjectIterable()

        return o


def get_catalogue_client_credentials():
    sess = boto3.Session()

    secrets_client = sess.client("secretsmanager")

    sierra_api_root = "https://libsys.wellcomelibrary.org/iii/sierra-api/v6"
    sierra_client_key = secrets_client.get_secret_value(
        SecretId="sierra_adapter/sierra_api_key"
    )["SecretString"]
    sierra_client_secret = secrets_client.get_secret_value(
        SecretId="sierra_adapter/sierra_api_client_secret"
    )["SecretString"]

    return {
        "api_url": sierra_api_root,
        "oauth_key": sierra_client_key,
        "oauth_secret": sierra_client_secret,
    }


def catalogue_client():
    return SierraClient(**get_catalogue_client_credentials())
