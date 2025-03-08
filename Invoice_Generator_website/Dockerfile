# Use an official Java runtime as a base image
FROM openjdk:17-jdk-slim AS build

# Set the working directory for the build
WORKDIR /Invoice_Generator_website

# Copy the Maven project files
COPY pom.xml ./
COPY src ./src

# Build the project (Replace 'your-app' with the actual JAR name)
RUN apt-get update && apt-get install -y maven && mvn clean package -DskipTests

# Use a new image to run the application
FROM openjdk:17-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the built JAR from the previous stage
COPY --from=build /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "app.jar"]
