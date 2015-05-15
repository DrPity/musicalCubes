public int bpm; int bpmOffset;

class Sequencer implements Instrument {
    void noteOn( float dur ) {
        if ( cubesState[beat] ) {
            for (int i = 0; i < cubes.length; i++) { //Stop other cubes if they are making sounds.
                cubeSamples.get(i).stop();
            }
            cubeSamples.get(beat).setSampleRate(currentSampleRateOf[beat]);
            cubeSamples.get(beat).trigger();
            byte   colorCube   = (byte) map (currentSemitoneOf[beat], 0, semitones.length, 25, 230);
            byte [] bytes = {hash, frSlash, byte(cubes[beat]), colorCube};
            sendSerial(bytes);
            println("sent trigger for cube " + beat);
        }
    }

//---------------------------------------------------------------------

    void noteOff() {
        //if ( cubesState[(beat+1)%8] )cubeSamples.get(beat).stop();
        // next beat
        beat = (beat + 1) % 8;
        // set the new tempo
        out.setTempo( bpm / 2); // + bpmOffset );
        // play this again right now, with a sixteenth note duration
        if (!stopSequencer) {
            out.playNote( 0, 0.25f, this );
        } else {
            sequencerIsStopped = true;
        }
    }
}
