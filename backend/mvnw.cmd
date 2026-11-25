@echo off
setlocal
set WRAPPER_DIR=%~dp0.mvn\wrapper
set WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar
if not exist "%WRAPPER_JAR%" (
  echo Maven Wrapper JAR is missing: %WRAPPER_JAR%
  echo Download it from https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar
  exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
endlocal
