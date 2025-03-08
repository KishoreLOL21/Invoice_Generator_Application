# Use an official Java runtime as a base image for building
FROM openjdk:17-jdk-slim AS build

# Set the working directory for the build
WORKDIR /app

# Copy the entire project directory
COPY . .

# Install Maven and build the project (Replace 'your-app' with actual JAR name)
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
