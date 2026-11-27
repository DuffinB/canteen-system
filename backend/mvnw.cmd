@echo off
setlocal
for %%i in ("%~dp0.") do set "MAVEN_PROJECTBASEDIR=%%~fi"
set "WRAPPER_DIR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper"
set "WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar"
if not exist "%WRAPPER_JAR%" (
  echo Maven Wrapper JAR is missing: %WRAPPER_JAR% 1>&2
  exit /b 1
)
if "%JAVA_HOME%"=="" (
  echo JAVA_HOME must point to a JDK 17 installation. 1>&2
  exit /b 1
)
"%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
set "WRAPPER_ERROR_CODE=%ERRORLEVEL%"
endlocal & exit /b %WRAPPER_ERROR_CODE%
