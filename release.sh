mvnd spring-boot:build-image -Dspring-boot.build-image.imageName=wutzebaer/cardano-db-sync-api:latest -DskipTests=true
docker push wutzebaer/cardano-db-sync-api:latest