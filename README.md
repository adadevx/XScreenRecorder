# XScreenRecorder
forked from https://gitlab.com/vijai/screenrecorder

Requires Lollipop and above (SDK 21+)

## Donation/Payments
#### PayPal:      [![Paypal Donate](https://www.paypalobjects.com/webstatic/en_US/i/btn/png/gold-pill-paypal-26px.png)](https://paypal.me/vijaichander/5)
#### Flattr:      [![Flattr this git repo](https://button.flattr.com/flattr-badge-large.png)](https://flattr.com/submit/auto?fid=66ngyo&url=https%3A%2F%2Fgithub.com%2Fvijai1996%2Fscreenrecorder)

## Building the app

### Make a copy of the repository

Make sure to have Git installed and clone the repo using

```
git clone https://gitlab.com/vijai/screenrecorder
```

### Building the apk
Building apk is possible in 3 ways
* 1.a. [Building debug apk using commandline](#1a-building-debug-apk-using-commandline)
* 1.b. [Building release apk using commandline](#1b-building-release-apk-using-commandline)
* 2.   [Building using AndroidStudio](#2-building-using-androidstudio)

### 1.a. Building debug apk using commandline
Switch to project root directory and run

#### Build Fdroid flavour
```
gradlew.bat assembleFdroidDebug
```

#### Build Play flavour
```
gradlew.bat assemblePlayDebug
```

### 1.b. Building release apk using commandline
Switch to project root directory and make sure to edit `app` module's build.gradle to include signing key information and run

#### Build Fdroid flavour
```
gradlew.bat assembleFdroidRelease
```

#### Build Play flavour
```
gradlew.bat assemblePlayRelease
```

#### 2. Building using AndroidStudio
Open Android Studio -> File -> Import Project -> Choose the cloned project folder and continue with the on-screen instructions

## Contributions
Any contribution to the app is welcome in the form of pull requests.

## License and copyright infringements
I will consider any kind of license or copyright infringements seriously and will send copyright claim notice or license infringement notice to anyone who is not adhering to them.

If you notice any content which seem to be infringing, please fill the below google forms to help me indentify and take them down.

[Google Form](https://goo.gl/forms/ntFKRXflFh2NH1dx1)

## Authors

* **Vijai Chander** - *Initial work* - [vijai](https://gitlab.com/vijai)

## License

This project is licensed under the GNU AGPLv3 - see the [LICENSE](LICENSE) file for details
