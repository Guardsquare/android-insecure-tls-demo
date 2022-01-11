# Containerized Demo for Insecure TLS Certificate Checking in Android

## Overview

This repository contains the files you need to run the demos for our blog post series
on TLS certificate checking in Android apps. The
[first post](https://www.guardsquare.com/blog/insecure-tls-certificate-checking-in-android-apps)
covers common implementation errors and the
[second one](https://guardsquare.com/blog/how-to-securely-implement-tls-certificate-checking-in-android-apps)
then explains how you can securely configure TLS connections even in cases when you have
to deviate from the default behavior. There are two parts to the repo:

1. **Example app:** In [app/](app/) you will find the full AndroidStudio project for the example app that showcases
   different TLS checking implementations.
2. **Docker setup:** By running [setup.sh](setup.sh) you prepare a Docker environment consisting of several containers:
   An Android emulator is spawned and a web frontend to interact with it is made available on https://localhost
   (Note that this frontend uses a self-signed certificate). Additionally, an example web server container is
   created, which will be used as the backend for the demo scenarios. The last part of the setup is an attacker
   container, through which you will be able to interactively intercept web traffic between backend server and
   Android emulator.

## Scenario Overview

The backend serves a simple HTML website over HTTPs. This mimicks the situation where sensitive data is provided
over a secure connection. The catch is that the certificate it uses (see [backend/nginx-certs/](backend/nginx-certs))
has not been issued by a globally trusted CA but rather by a custom one.

The Android app in [app/](app/) fetches the data provided by this server and displays it to the user. In order to
make Android accept the custom certificate, the default certificate checking mechanism needs to be modified.
To showcase different insecure ways of doing so, the app consists of several tabs, where each
fetches the data using a different workaround commonly found online. You can check out the corresponding
source code in [WebViewFragment.kt](app/app/src/main/kotlin/com/example/insecuretls/ui/main/WebViewFragment.kt).

In the first blog post we cover three different types of implementation errors:

1. **[WebView ignores all SSL errors](https://www.guardsquare.com/blog/insecure-tls-certificate-checking-in-android-apps#webview):**
   See [setupInsecureWebView()](app/app/src/main/kotlin/com/example/insecuretls/ui/main/WebViewFragment.kt#L58)
2. **[Malfunctioning X509TrustManager Implementations](https://www.guardsquare.com/blog/insecure-tls-certificate-checking-in-android-apps#Malfunctioning):**
   See [setupInsecureTrustManager()](app/app/src/main/kotlin/com/example/insecuretls/ui/main/WebViewFragment.kt#L108)
3. **[Disabled Host Name Checks](https://www.guardsquare.com/blog/insecure-tls-certificate-checking-in-android-apps#host_name):**
   See [setupInsecureHostnameVerifier()](app/app/src/main/kotlin/com/example/insecuretls/ui/main/WebViewFragment.kt#L82)

The second blog post explains how to configure non-standard certificate checking behaviors in a secure way:

1. **[Allowing Custom Certificate Authorities](https://www.guardsquare.com/blog/how-to-securely-implement-tls-certificate-checking-in-android-apps#Allowing_Custom_Certificate_Authorities):**
   1. **SDK 24 And Newer:** See [setupNetworkSecurityConfig()](app/app/src/main/kotlin/com/example/insecuretls/ui/main/WebViewFragment.kt#L165)
      and [network_security_config.xml](app/app/src/main/res/xml/network_security_config.xml)
   2. **Older Versions:** See [setupCustomCaLegacy()](app/app/src/main/kotlin/com/example/insecuretls/ui/main/WebViewFragment.kt#L211)
      and [setupCustomCaLegacyWebview()](app/app/src/main/kotlin/com/example/insecuretls/ui/main/WebViewFragment.kt#L260)
2. **[Certificate Pinning](https://www.guardsquare.com/blog/how-to-securely-implement-tls-certificate-checking-in-android-apps#Certificate_Pinning):**
   1. **SDK 24 And Newer:** See [setupNetworkSecurityConfig()](app/app/src/main/kotlin/com/example/insecuretls/ui/main/WebViewFragment.kt#L165)
      and [network_security_config.xml](app/app/src/main/res/xml/network_security_config.xml)
   2. **Older Versions:** See [setupPinningLegacy()](app/app/src/main/kotlin/com/example/insecuretls/ui/main/WebViewFragment.kt#L323)
      and [setupPinningLegacyWebview()](app/app/src/main/kotlin/com/example/insecuretls/ui/main/WebViewFragment.kt#L405)

This app will be installed to a containerized Android emulator that lives in the same virtual network as
the backend server and the attacker. Setting this network up is explained in the next section.

## Prerequisites

In order to launch the demo environment, you will need to have [docker-compose](https://docs.docker.com/compose/install/)
installed, as well as Python3, NodeJS and npm. Also make sure to have the Android SDK installed (SDK platform version 31).
The `ANDROID_SDK_ROOT` environment variable needs to point to its installation directory, usually `~/Android/Sdk`.
All other necessary dependencies will be downloaded automatically.

## Docker Setup

Running [setup.sh](setup.sh) will get the necessary files to set up the Docker containers, which may take a while,
depending on your system performance and Internet speed. Afterwards you can launch the containers with [run.sh](run.sh).
This script takes care of several things:

1. The Android emulator will be booted and a web interface to interact with it is made available on
   https://localhost (Note that the website uses a self-signed certificate). Login with username `user` and password
   `pass`. Then you should see the emulator screen, with which you can interact using your mouse.
2. In the meantime, the example app is compiled and once the emulator is fully booted up it is installed
   and launched automatically.
3. Once the app is running, a bash shell is opened on the attacker container so that you can interactively
   experiment with the man-in-the-middle setup. As a quick start, you can simply execute the [start.sh](eve/eve_files/start.sh)
   script that you will find in the current working directory where the shell was spawned (`/eve_files` on the container).
   
   This script sets up the attacker proxy using the [mitmproxy](https://mitmproxy.org/) tool without needing
   any user input. You can then observe intercepted traffic in the console that will show up.
   To exit the console and stop the attack, simply press Ctrl+C and confirm. Should you want to deviate
   from the default attacker script, feel free to inspect [start.sh](eve/eve_files/start.sh) and the associated
   [proxy.py](eve/eve_files/proxy.py) file.
   
   If you would like to experiment with the certificate pinning implementations,
   `start.sh` allows you to pass `--custom-ca`, which will instruct `mitmproxy` to use a certificate
   that was signed by the same custom CA that the example server uses. This  mimics the situation that the
   attacker is indeed able to get a valid certificate for your domain, which would be trusted under normal circumstances.
   The additional certificate pinning step however is able to successfully detect the attack and refuse the connection.
5. After you are done exploring the demos, simply exit the attacker shell as usual (Ctrl+D or typing `exit`).
   This will automatically shut down the containers in a clean way.

