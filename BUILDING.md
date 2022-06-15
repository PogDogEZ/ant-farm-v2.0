# Building

Instructions on how to build YesCom for different platforms.

### Basic requirements
 - Java >= 11 (recommended: [corretto](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html), for Jep.)  
 - Python >= 3.8 (recommended: [the latest](https://www.python.org/downloads/).)
 - If on Windows, building from source requires [Visual C++ Redistributable](https://www.microsoft.com/en-gb/download/details.aspx?id=48145).

### Building
1. Clone this repository: `git clone https://github.com/PogDogEZ/ant-farm-v2.0.git` *(or download the zip if you don't have git)*
2. Move into the directory: `cd ant-farm-v2.0`
3. Install Cython via pip: `python3 -m pip install Cython>=0.29.30`
4. Build with gradle: `gradlew.bat clean :yescom:ui:build`

### Running
Once built, run `java -jar yescom-ui/build/libs/yescom-ui.jar`.
