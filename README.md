# Cattail

This is a software to provide the Android Application with Clojure nREPL that can be used as the entry point of any Android application.

Add dependencies [Clojure modified for Android](https://github.com/myst3m/clojure)
You can modify your AndroidManifest.xml as below

app/src/main/AndroidManifest.xml

```sh
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="your.package">
    <application
        android:name="cattail.App"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity
            android:name="your.package.MainActivity"
            android:label="@string/app_name"
            android:theme="@style/AppTheme.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```
### Build
```sh
$  clj -A:cambada:android-16 -m cambada.jar
```
### Usage

1. Add the following lines in MainActivity.java
```java
import cattail.interop.Bridge;

public class MainActivity extends AppCompatActivity {
...

Bridge.load(this.getApplication());

}
```

2. Add repositories in global build.gradle
```java
    repositories {
        jcenter()
        maven { url "https://clojars.org/repo" }
        google()
    }
```

3. Add to dependencies in build.gradle
```java
defaultConfig {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

	multiDexEnabled true
}

dependencies {
    implementation 'theorems:clojure:1.8.0-a8'
	implementation 'nrepl:nrepl:0.7.0'
	implementation 'cider:cider-nrepl:0.25.2'
}
```

4. Copy this cattail-1.0.1.jar to app/libs
 
