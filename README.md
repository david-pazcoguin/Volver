# ARApp

ARApp is an Android application that provides an augmented reality tour guide experience. Users can explore various points of interest through AR models, which are displayed when the user is physically near the location. The app also features a user authentication system, allowing users to register and log in to their accounts.

## Features

*   **User Authentication:** Users can create an account and log in to the application.
*   **Location-Based AR:** The app uses the device's location to unlock AR experiences when the user is near a target location.
*   **AR Model Viewer:** Users can place and interact with 3D models in the real world through their device's camera.
*   **Text-to-Speech:** The app provides a spoken description of the AR models when they are tapped.
*   **Dashboard:** A dashboard provides access to settings and an "About Us" section.
*   **Account Management:** Users can update their personal information.

## How it Works

The application is composed of several key components:

*   **`ARActivity`:** This is the core of the AR experience. It uses ARCore to render 3D models and the device's location to determine if the user is near a target location.
*   **`HomeActivity`:** This is the main screen of the application. It displays a list of available AR experiences and provides access to the dashboard.
*   **`LoginActivity` and `RegisterActivity`:** These activities handle user authentication. They communicate with a remote server to validate user credentials and create new accounts.
*   **`AboutUsActivity`:** This activity displays information about the application.
*   **`AccountSettingActivity`:** This activity allows users to update their personal information.
*   **`SettingActivity`:** This activity provides options to navigate to the account settings or log out.
*   **`ARAdapter` and `ARHelper`:** These classes are used to populate the list of AR experiences on the home screen.
*   **`URLDatabase`:** This class stores the URLs for the application's backend services.

## Setup

To run the application, you will need to have an Android device that supports ARCore. You will also need to configure the target locations in the `HomeActivity.java` file and the server URLs in the `URLDatabase.java` file.
