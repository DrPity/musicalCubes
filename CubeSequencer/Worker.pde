class Worker extends Thread{

  private int wait;
  private boolean running;


  public int copyCubeNr1;
  public int copyCubeNr2;
  public boolean recordVoice;
  public boolean endRecordingVoice;
  public boolean startCopying;

  static final int NUMBEROFTIMERS = 6;
  private long [] waitTime = new long[NUMBEROFTIMERS];
  private long [] currentTime = new long[NUMBEROFTIMERS];


  Worker (int _wait) {
    wait = _wait;
    recordVoice = false;
    endRecordingVoice = false;
    startCopying = false;
    copyCubeNr1 = 0;
    copyCubeNr2 = 1;
    wait(1500,0);
  }

// ------------------------------------------------------------------------------------
 
  public void start () {
    running = true;
    println("Starting thread (will execute every " + wait + " milliseconds.)");
    super.start();
  }
 
// ------------------------------------------------------------------------------------

  public void run () {
    while (running) {
      sleep(wait);
      checkBooleans();
    }
    System.out.println("Worker thread is done!");  // The thread is done when we get to the end of run()
    quit();
  }

// ------------------------------------------------------------------------------------
 
  // Our method that quits the thread
  public void quit() {
    System.out.println("Quitting.");
    running = false;  // Setting running to false ends the loop in run()
    interrupt();
  }

// ------------------------------------------------------------------------------------

  private void sleep(int sleepTime){
  try {
      sleep((long)(sleepTime));
    } catch (Exception e) {
    }

  }


  public void refreshSampleBuffer(int cubeNumber){

    minim.loadFileIntoBuffer(cubeNumber + ".wav", sampleBuffer.get(cubeNumber));
    cubeSamples.get(cubeNumber).setSample(sampleBuffer.get(cubeNumber), cubeSamples.get(cubeNumber).sampleRate());

  }

//---------------------------------------------------------------------
//copy .wav
//---------------------------------------------------------------------

  public void copyCubes(int cubeNumber1, int cubeNumber2){
    //out.mute();
    stopStepSequencer();
    println("Try to copy from" + cubeNumber1 + "to" + cubeNumber2);
    byte [] b = loadBytes( cubeNumber1 + ".wav"); 
    saveBytes(cubeNumber2 + ".wav", b);
    refreshSampleBuffer(cubeNumber2);
    
    byte [] bytes = {hash, star, byte(cubeNumber1), byte(cubeNumber2)};
    sendSerial(bytes);
    startCopying = false;
  }

//---------------------------------------------------------------------
//start recording sound
//---------------------------------------------------------------------

  public void startRecording(){
    if(cubesState[cubeToRecord]){
      distanceReferenceArray[cubeToRecord] = distanceArray[cubeToRecord];
    }
    else{
      distanceReferenceArray[cubeToRecord] = DEFAULTDISTANCEREFERENCE;  
    }  
    recorder = minim.createRecorder(in, cubes[cubeToRecord] + ".wav", true);
    recordingTime = millis();
    println("Recording! Psst!! -- Next Volume:");
    recorder.beginRecord();
    recording = true;
    recordingTime = millis();
    if(recording){
      byte [] bytes = {hash, lBracket, lastTriggeredCube};
      sendSerial(bytes);
    }

    recordVoice = false;
  }

  void checkBooleans(){
    if (recordVoice){
      startRecording();
    }
    if(endRecordingVoice){
      endRecording();
    }
    if(startCopying){
      copyCubes(copyCubeNr1, copyCubeNr2);
    }
  }


  void endRecording(){
      recorder.endRecord();
      println("Done recording.");
      recorder.save();
      println("Done saving.");
      recording = false;
      refreshSampleBuffer(cubeToRecord);
      byte [] bytes = {hash, rBracket, lastTriggeredCube};
      sendSerial(bytes);
      startStepSequencer();
      myPort.clear();
      out.unmute();
      endRecordingVoice = false;

  }


  //set up to 6 Timers:
  void wait(int _waitTime, int _numberOfIndex){
    waitTime[_numberOfIndex] = _waitTime;
    currentTime[_numberOfIndex] = millis();

  }

  //check Time:
  boolean checkTimers(int _index){
    if(millis() - currentTime[_index] >= waitTime[_index]){
      // Serial.print("TimeStamp: ");
      // Serial.println(currentTime[_index]);
      currentTime[_index] = millis();
      return true;
    }else{
      return false;
    }
  }

}