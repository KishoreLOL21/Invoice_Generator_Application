# Use an official Java runtime as a base image
FROM openjdk:17-jdk-slim

# Set the working directory inside the container
WORKDIR /Invoice_Generator_website

# Copy the compiled JAR file to the container (ensuring correct path)
COPY target/*.jar app.jar

# Expose the application port (default 8080, update if necessary)
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "app.jar"]
