Collect Data app on phone side that can receive data from smart watch.

Smart watch and phone device needs to connect through bluetooth.

Click "Send To Phone" on smart watch and wait for the data to be transfered.


Command that push data from phone:

adb shell rm -rf /storage/emulated/0/Android/data/com.example.collectdata/files
adb pull /storage/emulated/0/Android/data/com.example.collectdata/files/
