# oauth20
Test task. Oauth 2.0 server implementation
## Build
1. sbt clean assembly
2. cd dependencies
3. docker-compose -f all-compose.yaml build
## Run
docker-compose -f all-compose.yaml up
## Test
### Create a new client
curl -k -X POST -H "content-type: application/json" "https://localhost:8080/client" -d '{"client_id": "fan", "secret_id":"Secret1234", "callback_url": "http://localhost:8080?session_id=10"}'

### Create a new user (resource owner)
curl -k -X POST -H "content-type: application/json" "https://localhost:8080/user" -d '{"user_name": "vika", "password":"Vika12345_", "name":"Vika Filyanina"}'

### Request authorization code with user credentials for client
curl -k -vvvv -u vika:Vika12345_ -X GET -H "content-type: application/json" "https://localhost:8080/oauth20/authorize?response_type=code&client_id=fan&state=superState"

> < HTTP/1.1 302 Found
> < Location: http://localhost:8080?session_id=10&state=superState&code=24db960f-9e7a-406c-aa04-ff6a0a68c630
> < 
> The requested resource temporarily resides under <a href="http://localhost:8080?session_id=10&state=superState&code=24db960f-9e7a-406c-aa04-ff6a0a68c630">this URI</a>.

### Getting code from response and use it to get access_toke. Client should be authenticated with request
curl -k -vvvv -u fan:Secret1234 -X POST -H "content-type: application/json" "https://localhost:8080/oauth20/token?grant_type=authorization_code&code=24db960f-9e7a-406c-aa04-ff6a0a68c630"

> < HTTP/1.1 200 OK
> < 
> {"access_token":"c0e2cf20-51d4-4969-8df8-4b58b013ebe4","refresh_token":"e4bcab99-cba9-459b-b3ca-697804df8bf0","token_type":"bearer","expires_at":"2020-05-04T07:22:12.301Z"}

### Test with received accessToken
curl -k -vvvv -H "Authorization: Bearer c0e2cf20-51d4-4969-8df8-4b58b013ebe4" -X GET -H "content-type: application/json" "https://localhost:8080/resource"

> < HTTP/1.1 200 OK
> 
> "{\"access\":\"granted\",\"resource\":\"ok\"}"

### Test with wrong accessToken
curl -k -vvvv -H "Authorization: Bearer FailedAccessToken" -X GET -H "content-type: application/json" "https://localhost:8080/resource"

>< HTTP/1.1 401 Unauthorized<br>
>< WWW-Authenticate: Bearer realm="oauth20"
>
>The supplied authentication is invalid        

### Test token refresh with refresh_token
curl -k -vvvv -u fan:Secret1234 -X POST -H "content-type: application/json" "https://localhost:8080/oauth20/token?grant_type=refresh_token&refresh_token=e4bcab99-cba9-459b-b3ca-697804df8bf0"

> < HTTP/1.1 200 OK
> < 
> {"access_token":"833e9ab4-d059-45e6-9173-81253a0388f4","refresh_token":"812e6547-64eb-4a22-80f4-3c6fe6fa6fa3","token_type":"bearer","expires_at":"2020-05-04T07:25:53.608Z"}
