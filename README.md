# Survey

Survey is a data collection application based upon HTML, CSS and Javascript.

Instructions on how to use Survey have to be added in future.

## Setting up your environment

Install [Android Studio](http://developer.android.com/tools/studio/index.html) and the [SDK](http://developer.android.com/sdk/index.html#Other).


ODK Services __MUST__ be installed on your device, whether by installing the APK or by cloning the project and deploying it. 


## Building the project

Open the Survey project in Android Studio. Select `Build->Make Project` to build the app.

## Running

Be sure to install ODK Services onto your device before attempting to run Survey.

## Source tree information
Quick description of the content in the root folder:

    |-- survey_app     -- Source tree for Java components

        |-- src

            |-- main

                |-- res     -- Source tree for Android resources

                |-- java

                    |-- org

                        |-- opendatakit

                            |-- survey

                                |-- android     -- The most relevant Java code lives here

            |-- androidTest    -- Source tree for Android implementation tests
