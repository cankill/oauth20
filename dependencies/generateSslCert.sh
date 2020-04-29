keytool -genkeypair -alias root -dname "cn=Local Network - Development" -validity 10000 -keyalg RSA -keysize 2048 -ext bc:c -keystore root.jks -keypass changeMe -storepass changeMe

keytool -genkeypair -alias ca -dname "cn=Local Network - Development" -validity 10000 -keyalg RSA -keysize 2048 -ext bc:c -keystore ca.jks -keypass changeMe -storepass changeMe

keytool -exportcert -rfc -keystore root.jks -alias root -storepass changeMe > root.pem

keytool -keystore ca.jks -storepass changeMe -certreq -alias ca \
| keytool -keystore root.jks -storepass changeMe -gencert -alias root -ext bc=0 -ext san=dns:ca -rfc > ca.pem

keytool -keystore ca.jks -storepass changeMe -importcert -trustcacerts -noprompt -alias root -file root.pem
keytool -keystore ca.jks -storepass changeMe -importcert -alias ca -file ca.pem


keytool -genkeypair -alias server -dname cn=server -validity 10000 -keyalg RSA -keysize 2048 -keystore my-keystore.jks -keypass changeMe -storepass changeMe

keytool -keystore my-keystore.jks -storepass changeMe -certreq -alias server \
| keytool -keystore ca.jks -storepass changeMe -gencert -alias ca -ext ku:c=dig,keyEnc -ext "san=dns:localhost,ip:0.0.0.0" -ext eku=sa,ca -rfc > server.pem


keytool -keystore my-keystore.jks -storepass changeMe -importcert -trustcacerts -noprompt -alias root -file root.pem
keytool -keystore my-keystore.jks -storepass changeMe -importcert -alias ca -file ca.pem
keytool -keystore my-keystore.jks -storepass changeMe -importcert -alias server -file server.pem


keytool -keystore my-truststore.jks -storepass changeMe -importcert -trustcacerts -noprompt -alias root -file root.pem
keytool -keystore my-truststore.jks -storepass changeMe -importcert -alias ca -file ca.pem
keytool -keystore my-truststore.jks -storepass changeMe -importcert -alias server -file server.pem
