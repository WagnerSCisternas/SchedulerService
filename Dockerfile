# Estágio de Build: Constrói o JAR executável da sua aplicação Spring Boot
# Usamos uma imagem base do Maven que já inclui o JDK 17 para compilar o código.
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Define o diretório de trabalho dentro do contêiner para este estágio.
WORKDIR /app

# Copia o arquivo pom.xml para o contêiner. Isso permite que o Maven
# baixe as dependências e as armazene em cache, otimizando builds subsequentes
# se o pom.xml não mudar.
COPY pom.xml .

# Copia o código fonte da sua aplicação para o diretório de trabalho.
# Certifique-se de que a estrutura de pastas src/main/java e src/test/java esteja correta.
COPY src ./src

# Executa o build Maven. O comando 'clean package' compila o código e empacota a aplicação
# em um JAR executável. O '-DskipTests' pula a execução dos testes durante o build,
# o que é comum em builds de deploy para agilizar o processo.
RUN mvn clean package -DskipTests

# Estágio de Execução: Cria uma imagem final menor para rodar a aplicação
# Usamos uma imagem base do OpenJDK que contém apenas o JRE (Java Runtime Environment)
# para ser mais leve, já que não precisamos do compilador em tempo de execução.
FROM eclipse-temurin:21-jre-focal

# Define o diretório de trabalho dentro do contêiner para este estágio.
WORKDIR /app

# Copia o JAR executável gerado no estágio de build para o diretório de trabalho
# do estágio de execução. O nome do JAR é geralmente artifactId-version.jar.
# É crucial que o nome do JAR aqui corresponda exatamente ao que o Maven gera.
# Por exemplo, se seu projeto se chama 'schedulerService' e a versão é '0.0.1-SNAPSHOT',
# o JAR será 'schedulerService-0.0.1-SNAPSHOT.jar'.
COPY --from=build /app/target/schedulerService-0.0.1-SNAPSHOT.jar app.jar

# Expõe a porta que sua aplicação Spring Boot usará.
# Por padrão, o Spring Boot usa a porta 8080. Mesmo que seja um Background Worker
# sem endpoints web expostos publicamente, é uma boa prática declarar a porta.
EXPOSE 8080

# Comando para rodar a aplicação Spring Boot quando o contêiner for iniciado.
# O 'java -jar' executa o JAR como um aplicativo Spring Boot.
CMD ["java", "-jar", "app.jar"]