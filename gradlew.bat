@rem Gradle startup script for Windows
@rem Copyright (c) 2015 the original author or authors.

@if "%DEBUG%" == "" @echo off
@rem Set local scope for the variables with windows NT shell
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

call :find_java_home

:execute
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% ^
  "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" ^
  org.gradle.wrapper.GradleWrapperMain %*

:mainEnd
if "%OS%" == "Windows_NT" endlocal
