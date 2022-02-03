./mvnw package -DskipTests && docker build -f src/main/docker/Dockerfile.jvm -t quay.io/lburnett/rhsm:quarkus_poc . && docker push quay.io/lburnett/rhsm:quarkus_poc && ./deploy/bonfire_deploy.sh
