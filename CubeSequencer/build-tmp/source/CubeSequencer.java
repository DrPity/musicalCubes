import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import ddf.minim.*; 
import ddf.minim.spi.*; 
import ddf.minim.ugens.*; 
import processing.serial.*; 
import ddf.minim.analysis.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class CubeSequencer extends PApplet {








Minim minim;
AudioInput in;
AudioOutput out;
AudioRecorder recorder;
ArrayList<Sampler> cubeSamples;
ArrayList<MultiChannelBuffer> sampleBuffer;
Sequencer sequencer;
Worker worker;

//---------------------------------------------------------------------

int cubeToRecord              =   0;
int averageBpm                =   0;
int recordingTime             =   0;
int sleepTime                 =   0;
int bufferLength              =   3;
int[] cubes                   =   new int [8];
int[] distanceArray           =   new int [8];
int[] distanceReferenceArray  =   new int [8];
int inByte                    =   0;
int count                     =   0;
int beat;
byte lastTriggeredCube        =   0;
byte hash                     =   35;
byte frSlash                  =   47;
byte bkSlash                  =   92;
byte effect                   =   0;
byte lBracket                 =   91;
byte rBracket                 =   93;
byte star                     =   42;
byte exPr                     =   33;
byte gtThen                   =   62;

float volumeTreshold          =   30;
float volumeMix               =   0;

boolean recording             =   false;
boolean ready                 =   false;
boolean boxIsTapped           =   false;
boolean stopSequencer         =   false;
boolean sequencerIsStopped    =   false;
boolean[] cubesState          =   new boolean[8];

static final int DEFAULTDISTANCEREFERENCE   = 300;
static final int DEFAULTSAMPLERATE          = 44100;
static final int PITCHSCALE                 = 100; 

Serial myPort;

//---------------------------------------------------------------------

public int bpm; int bpmOffset;

class Sequencer implements Instrument
{
  public void noteOn( float dur )
  {
    if ( cubesState[beat] ){
      for(int i =0; i < cubes.length; i++){
        cubeSamples.get(i).stop();
      }
      cubeSamples.get(beat).trigger();
      byte [] bytes = {hash, frSlash, PApplet.parseByte(cubes[beat]), effect};
      sendSerial(bytes);
    } 
  }
  
  public void noteOff()
  {
    //if ( cubesState[(beat+1)%8] )cubeSamples.get(beat).stop();
    // next beat
    beat = (beat+1)%8;
    // set the new tempo
    out.setTempo( bpm/2); // + bpmOffset );
    // play this again right now, with a sixteenth note duration
    if (!stopSequencer){
      out.playNote( 0, 0.25f, this );
    }else{
      sequencerIsStopped = true;
    }
  }
}


//---------------------------------------------------------------------


public void setup()
{
  size(512, 200);
 
  minim = new Minim(this);
  println(Serial.list());
  myPort = new Serial(this, Serial.list()[6], 9600);
  myPort.clear();
  myPort.bufferUntil(255);

  worker = new Worker(1);
  worker.start();
  
  //16 bit 44100khz sample buffer 512 stereo;
  in = minim.getLineIn(Minim.STEREO, 1024);
  out = minim.getLineOut();

  cubeSamples = new ArrayList<Sampler>();
  sampleBuffer = new ArrayList<MultiChannelBuffer>();
  sequencer = new Sequencer();

  for (int i=0; i < cubes.length; i++) {         
    cubes[i] = i;
    sampleBuffer.add(new MultiChannelBuffer( 4, 1024 ));
    float sampleRate = minim.loadFileIntoBuffer( i + ".wav", sampleBuffer.get(i) );
    cubeSamples.add(new Sampler(sampleBuffer.get(i), sampleRate, 4));
    cubeSamples.get(i).patch(out);
    // bpmArray[i] = 60;
    println("Test Sample I: " + cubes[i]);
  }

  recorder = minim.createRecorder(in, "bajs.wav", true);

  bpm = 130;
  beat = 0;

   // start the sequencer
  out.setTempo( bpm );
  out.playNote( 0, 0.25f, sequencer );
  out.mute();
  textFont(createFont("Arial", 12));
  
  //////Start the Arduino Sketch!!!!!!!!!
  byte [] bytes = {'a'};
  sendSerial(bytes);
}

//---------------------------------------------------------------------
 
public void draw()
{
  background(0); 
  stroke(255);
  for(int i = 0; i < in.left.size()-1; i++)
  {
    line(i, 50 + in.left.get(i)*50, i+1, 50 + in.left.get(i+1)*50);
    line(i, 150 + in.right.get(i)*50, i+1, 150 + in.right.get(i+1)*50);
  }

  if ( recorder.isRecording() )
  {
    text("Currently recording...", 5, 15);
  }
  else
  {
    text("Not recording.", 5, 15);
  }

  while(recording){
    if(millis() - recordingTime >= 2000){
      worker.endRecordingVoice = true;
    }
  }

  volumeMix = in.mix.level();
  volumeMix = PApplet.parseInt(volumeMix*1000);

  if (boxIsTapped){ 
    out.mute();
    stopStepSequencer();
    waitForVolumeTreshold();
  }

  if (!ready){
    println("Ready");
    ready = true;
    out.unmute();
    // startAllBeats();
  }
  // Use for Debugging:

  // if (keyPressed) {
  //   if (key == 's' || key == 'S') {
  //     for (int i = 0; i<cubes.length; i++){  
  //     println("SampleRateChanged:");
  //     cubeSamples.get(i).setSampleRate(1000); 
  //     }
  //   }
  //   if (key == 'b' || key == 'B' ) {
  //     for (int i = 0; i<cubes.length; i++){
  //       cubesState[i] = true;
  //     }
  //   }
  // }
}

//---------------------------------------------------------------------
//Wait for serial events 
//---------------------------------------------------------------------

public void serialEvent(Serial myPort) {

  while (myPort.available() > 0) { 
  inByte = myPort.read();
  // print("Time: "+ millis() + " - ReceivedByte: " + inByte);
  // println();
  
    if (ready){
      //copy cubes
      if(inByte == star){
        println("Copying Triggered");
        worker.copyCubeNr1 = myPort.read();
        worker.copyCubeNr2 = myPort.read();
        // print("copyCubeNr1: "+ worker.copyCubeNr1 + " copyCubeNr2: " + worker.copyCubeNr2);
        println();
        worker.startCopying = true;
      }

      //recording cube
      if(inByte == hash && !boxIsTapped){
        println("Recording Triggered");
        boxIsTapped = true;
        sleepTime = millis();
        cubeToRecord = myPort.read();
        lastTriggeredCube = (byte) cubeToRecord; 
      }

      //trigger cube
      if(inByte == frSlash){
        int cube = myPort.read();
        lastTriggeredCube = (byte)  cube;
        int value = myPort.read();
        startBeat(cube, value);
      }

      //trigger cube off
      if(inByte == bkSlash){
        int cube = myPort.read();
        stopBeat(cube);
        byte [] bytes = {hash, bkSlash, PApplet.parseByte(cube)};
        sendSerial(bytes);
      }


      if(inByte == gtThen){
        startStepSequencer();
      }
    }  
  }
}

//--------------------------------------------------------------------- 
//wait for volume trigger
//---------------------------------------------------------------------

public void waitForVolumeTreshold(){
  if((millis() - sleepTime) <= 8000){
    //println("Waiting and starting wait animation");
    // println("Recording Volume is:");
    // println(volumeMix);
    if (volumeMix >= volumeTreshold){
      println("Treshold Reached:");
      boxIsTapped = false;
      worker.recordVoice = true;
    }
  }else{
      //send "recording timeout" to Arduino
      println("Timeout");
      byte [] bytes = {hash, exPr, lastTriggeredCube};
      sendSerial(bytes);
      boxIsTapped = false;
      startStepSequencer();
      out.unmute();
  }
  
}

//---------------------------------------------------------------------
//Trigger a Beat
//---------------------------------------------------------------------

public void startBeat(int cubeNumber, int value){
  
  if(!cubesState[cubeNumber]){
    cubesState[cubeNumber] = true;
  }
  distanceArray[cubeNumber] = value;
  // averageBpm = 0;
  for (int i = 0; i<cubes.length; i++){
    int distance = distanceArray[i] - distanceReferenceArray[i];
    //calc average BPM of cubes
    if(cubesState[i]){
      cubeSamples.get(i).setSampleRate(DEFAULTSAMPLERATE - distance*PITCHSCALE);
      // averageBpm += bpmArray[i];
      count ++;
    } 
  }
  count = 0;
  // bpmOffset = averageBpm/count/2;
  // use for debugging
  //cubesState[currentCubeNumber] = true;
}


//---------------------------------------------------------------------
//Start all Beats 
//---------------------------------------------------------------------

public void startAllBeats(){
  for (int i = 0; i<cubes.length; i++){
    cubesState[i] = true;
  }
}


//---------------------------------------------------------------------
//Stop a Beat 
//--------------------------------------------------------------------- 

public void stopBeat(int cubeNumber){
  cubesState[cubeNumber] = false;
}

//---------------------------------------------------------------------
//Stop all Beats
//---------------------------------------------------------------------

public void stopAllBeats(){
  for (int i = 0; i<cubes.length; i++){
    cubesState[i] = false;
  }

}


public void stopStepSequencer(){
  stopSequencer = true;
}

public void startStepSequencer(){
  if(sequencerIsStopped){
    sequencerIsStopped = false;
    out.playNote( 0, 0.25f, sequencer);
  }
  stopSequencer = false;
}

//release recorder 
public void stop()
{
  // always close Minim audio classes when you are done with them
  in.close();
  out.close();
  // always stop Minim before exiting
  minim.stop();
  super.stop();
}


public void sendSerial(byte[] bytes){
  for (int i = 0; i < bytes.length; ++i) {
    myPort.write(bytes[i]);
  }
  myPort.write('\n');
}
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
    
    byte [] bytes = {hash, star, PApplet.parseByte(cubeNumber1), PApplet.parseByte(cubeNumber2)};
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

  public void checkBooleans(){
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


  public void endRecording(){
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
  public void wait(int _waitTime, int _numberOfIndex){
    waitTime[_numberOfIndex] = _waitTime;
    currentTime[_numberOfIndex] = millis();

  }

  //check Time:
  public boolean checkTimers(int _index){
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
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "--full-screen", "--bgcolor=#666666", "--stop-color=#CCCCCC", "CubeSequencer" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
