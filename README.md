# oauth20
Test task. Oauth 2.0 server implementation
## Build
1. sbt clean assembly
2. cd dependencies
3. docker-compose -f all-compose.yaml build
## Run
docker-compose -f all-compose.yaml up
## Test
curl -k -X POST -H "content-type: application/json" "https://localhost:8080/user" -d '{"user_name": "VikaFan", "password":"fan123!8910#F", "user_name":"Vika Filyanina! My Love"}'
curl -k -v -X GET -H "content-type: application/json" "https://localhost:8080/user/VikaFan"
curl -k -v -X DELETE -H "content-type: application/json" "https://localhost:8080/user/VikaFan"

