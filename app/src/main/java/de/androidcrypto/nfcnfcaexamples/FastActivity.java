package de.androidcrypto.nfcnfcaexamples;

import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.NfcA;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FastActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    TextView nfcContentParsed, nfcContentRaw;

    private NfcAdapter mNfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fast);
        nfcContentParsed = findViewById(R.id.tvFastNfcaContentParsed);
        nfcContentRaw = findViewById(R.id.tvFastNfcaContentRaw);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    // This method is run in another thread when a card is discovered
    // !!!! This method cannot cannot direct interact with the UI Thread
    // Use `runOnUiThread` method to change the UI from this method
    @Override
    public void onTagDiscovered(Tag tag) {
        // Read and or write to Tag here to the appropriate Tag Technology type class
        // in this example the card should be an Ndef Technology Type

        System.out.println("NFC tag discovered");

        NfcA nfcA = null;

        try {
            nfcA = NfcA.get(tag);

            if (nfcA != null) {
                writeToUiToast("NFC tag is Nfca compatible");

                // Make a Sound
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(150, 10));
                } else {
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(200);
                }

                nfcA.connect();

                runOnUiThread(() -> {
                    nfcContentRaw.setText("");
                });

                // check that the tag is a NTAG213/215/216 manufactured by NXP - stop if not
                String ntagVersion = NfcIdentifyNtag.checkNtagType(nfcA, tag.getId());
                if (ntagVersion.equals("0")) {
                    writeToUiAppend(nfcContentRaw, "NFC tag is NOT of type NXP NTAG213/215/216");
                    writeToUiToast("NFC tag is NOT of type NXP NTAG213/215/216");
                    return;
                }

                int nfcaMaxTranceiveLength = nfcA.getMaxTransceiveLength(); // important for the readFast command
                int ntagPages = NfcIdentifyNtag.getIdentifiedNtagPages();
                int ntagMemoryBytes = NfcIdentifyNtag.getIdentifiedNtagMemoryBytes();
                String tagIdString = Utils.getDec(tag.getId());
                String nfcaContent = "raw data of " + NfcIdentifyNtag.getIdentifiedNtagType() + "\n" +
                        "number of pages: " + ntagPages +
                        " total memory: " + ntagMemoryBytes +
                        " bytes\n" +
                        "tag ID: " + Utils.bytesToHex(NfcIdentifyNtag.getIdentifiedNtagId()) + "\n" +
                        "tag ID: " + tagIdString + "\n";
                nfcaContent = nfcaContent + "maxTranceiveLength: " + nfcaMaxTranceiveLength + " bytes\n";
                // read the complete memory depending on ntag type
                byte[] ntagMemory = new byte[ntagMemoryBytes];
                // read the content of the tag in several runs
                byte[] response;
                try {
                    //int nfcaMaxTranceiveLength = nfcA.getMaxTransceiveLength(); // my device: 253 bytes
                    int nfcaMaxTranceive4ByteTrunc = nfcaMaxTranceiveLength / 4; // 63
                    int nfcaMaxTranceive4ByteLength = nfcaMaxTranceive4ByteTrunc * 4; // 252 bytes
                    int nfcaNrOfFullReadings = ntagMemoryBytes / nfcaMaxTranceive4ByteLength; // 888 bytes / 252 bytes = 3 full readings
                    int nfcaTotalFullReadingBytes = nfcaNrOfFullReadings * nfcaMaxTranceive4ByteLength; // 3 * 252 = 756
                    int nfcaMaxTranceiveModuloLength = ntagMemoryBytes - nfcaTotalFullReadingBytes; // 888 bytes - 756 bytes = 132 bytes
                    nfcaContent = nfcaContent + "nfcaMaxTranceive4ByteTrunc: " + nfcaMaxTranceive4ByteTrunc + "\n";
                    nfcaContent = nfcaContent + "nfcaMaxTranceive4ByteLength: " + nfcaMaxTranceive4ByteLength + "\n";
                    nfcaContent = nfcaContent + "nfcaNrOfFullReadings: " + nfcaNrOfFullReadings + "\n";
                    nfcaContent = nfcaContent + "nfcaTotalFullReadingBytes: " + nfcaTotalFullReadingBytes + "\n";
                    nfcaContent = nfcaContent + "nfcaMaxTranceiveModuloLength: " + nfcaMaxTranceiveModuloLength + "\n";

                    for (int i = 0; i < nfcaNrOfFullReadings; i++) {
                        //nfcaContent = nfcaContent + "starting round: " + i + "\n";
                        System.out.println("starting round: " + i);
                        byte[] commandF = new byte[]{
                                (byte) 0x3A,  // FAST_READ
                                (byte) ((4 + (nfcaMaxTranceive4ByteTrunc * i)) & 0x0ff), // page 4 is the first user memory page
                                (byte) ((4 + (nfcaMaxTranceive4ByteTrunc * (i + 1)) - 1) & 0x0ff)
                        };
                        //nfcaContent = nfcaContent + "i: " + i + " commandF: " + bytesToHex(commandF) + "\n";
                        response = nfcA.transceive(commandF);
                        if (response == null) {
                            // either communication to the tag was lost or a NACK was received
                            // Log and return
                            nfcaContent = nfcaContent + "ERROR: null response";
                            String finalNfcaText = nfcaContent;
                            writeToUiAppend(nfcContentRaw, finalNfcaText);
                            System.out.println(finalNfcaText);
                            return;
                        } else if ((response.length == 1) && ((response[0] & 0x00A) != 0x00A)) {
                            // NACK response according to Digital Protocol/T2TOP
                            // Log and return
                            nfcaContent = nfcaContent + "ERROR: NACK response: " + Utils.bytesToHex(response);
                            String finalNfcaText = nfcaContent;
                            writeToUiAppend(nfcContentRaw, finalNfcaText);
                            System.out.println(finalNfcaText);
                            return;
                        } else {
                            // success: response contains ACK or actual data
                            System.arraycopy(response, 0, ntagMemory, (nfcaMaxTranceive4ByteLength * i), nfcaMaxTranceive4ByteLength);
                        }
                    } // for

                    // now we read the nfcaMaxTranceiveModuloLength bytes, for a NTAG216 = 132 bytes
                    //nfcaContent = nfcaContent + "starting last round: " + "\n";
                    //System.out.println("starting last round: ");
                    byte[] commandF = new byte[]{
                            (byte) 0x3A,  // FAST_READ
                            (byte) ((4 + (nfcaMaxTranceive4ByteTrunc * nfcaNrOfFullReadings)) & 0x0ff), // page 4 is the first user memory page
                            (byte) ((4 + (nfcaMaxTranceive4ByteTrunc * nfcaNrOfFullReadings) + (nfcaMaxTranceiveModuloLength / 4) & 0x0ff))
                    };
                    //nfcaContent = nfcaContent + "last: " + " commandF: " + bytesToHex(commandF) + "\n";
                    response = nfcA.transceive(commandF);
                    if (response == null) {
                        // either communication to the tag was lost or a NACK was received
                        // Log and return
                        nfcaContent = nfcaContent + "ERROR: null response";
                        String finalNfcaText = nfcaContent;
                        writeToUiAppend(nfcContentRaw, finalNfcaText);
                        System.out.println(finalNfcaText);
                        return;
                    } else if ((response.length == 1) && ((response[0] & 0x00A) != 0x00A)) {
                        // NACK response according to Digital Protocol/T2TOP
                        // Log and return
                        nfcaContent = nfcaContent + "ERROR: NACK response: " + Utils.bytesToHex(response);
                        String finalNfcaText = nfcaContent;
                        writeToUiAppend(nfcContentRaw, finalNfcaText);
                        System.out.println(finalNfcaText);
                        return;
                    } else {
                        // success: response contains ACK or actual data
                        System.arraycopy(response, 0, ntagMemory, (nfcaMaxTranceive4ByteLength * nfcaNrOfFullReadings), nfcaMaxTranceiveModuloLength);
                    }

                    nfcaContent = nfcaContent + "fast reading complete: " + "\n" + Utils.bytesToHex(ntagMemory) + "\n";
                } catch (TagLostException e) {
                    // Log and return
                    nfcaContent = nfcaContent + "ERROR: Tag lost exception";
                    String finalNfcaText = nfcaContent;
                    writeToUiAppend(nfcContentRaw, finalNfcaText);
                    System.out.println(finalNfcaText);
                    return;
                } catch (IOException e) {

                    e.printStackTrace();

                }
                String finalNfcaRawText = nfcaContent;
                String finalNfcaText = "parsed content:\n" + new String(ntagMemory, StandardCharsets.US_ASCII);
                writeToUiAppend(nfcContentRaw, finalNfcaRawText);
                writeToUiAppend(nfcContentParsed, finalNfcaText);
                System.out.println(finalNfcaText);
            } else {
                writeToUiToast("NFC tag is NOT Nfca compatible");
            }
        } catch (IOException e) {
            //Trying to catch any ioexception that may be thrown
            e.printStackTrace();
            writeToUiAppend(nfcContentRaw, "ERROR: IOException " + e);
        } catch (Exception e) {
            //Trying to catch any exception that may be thrown
            e.printStackTrace();
            writeToUiAppend(nfcContentRaw, "ERROR: IOException " + e);
        } finally {
            try {
                nfcA.close();
            } catch (IOException e) {
                writeToUiAppend(nfcContentRaw, "ERROR: IOException " + e);
            }
        }
    }

    private void writeToUiAppend(TextView textView, String message) {
        runOnUiThread(() -> {
            String newString = message + "\n" + textView.getText().toString();
            textView.setText(newString);
        });
    }

    private void writeToUiToast(String message) {
        runOnUiThread(() -> {
            Toast.makeText(getApplicationContext(),
                    message,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mNfcAdapter != null) {

            if (!mNfcAdapter.isEnabled())
                showWirelessSettings();

            Bundle options = new Bundle();
            // Work around for some broken Nfc firmware implementations that poll the card too fast
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);

            // Enable ReaderMode for all types of card and disable platform sounds
            // the option NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK is NOT set
            // to get the data of the tag afer reading
            mNfcAdapter.enableReaderMode(this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NFC_B |
                            NfcAdapter.FLAG_READER_NFC_F |
                            NfcAdapter.FLAG_READER_NFC_V |
                            NfcAdapter.FLAG_READER_NFC_BARCODE |
                            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null)
            mNfcAdapter.disableReaderMode(this);
    }
}