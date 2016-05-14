package edu.sfsu.cs.orange.ocr;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

public class OcrEngine {

  private static final String LOG_TAG = "OcrEngine";
  private String textResult;

  public String postProcessTextResult(String rawOCRResult) {
    // filter junk you can configure tess two to only recognize certain
    // characters but it is very agressive and will find a lot more wrong
    // characters
    // rawOCRResult = rawOCRResult.replaceAll("[^A-Z0-9]"," ");

    String initialResult = findIntial(rawOCRResult);
    String numberResult = findNumber(rawOCRResult);

    textResult = initialResult + numberResult + "\nall found text: " + rawOCRResult;
    return textResult;
  }

  public void saveImage(Bitmap bitmap) {
    String fileName = System.currentTimeMillis() + "_textResult_" + textResult.replaceAll("[^A-Za-z0-9]", "_") + ".png";
    try {
      File album = getAlbumStorageDir("OCRTest");

      File fileToSave = new File(album, fileName);
      FileOutputStream outputStream = new FileOutputStream(fileToSave);
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
      outputStream.close();
    } catch (FileNotFoundException e) {
      Log.e("OcrRecognizeAsyncTask", "Caught FileNotFoundException: " + fileName);
      e.printStackTrace();
    } catch (IOException e) {
      Log.e("OcrRecognizeAsyncTask", "Caught IOException: " + fileName);
      e.printStackTrace();
    }
  }

  private File getAlbumStorageDir(String albumName) {
    // Get the directory for the user's public pictures directory.
    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), albumName);
    if (!file.mkdirs()) {
      Log.e(LOG_TAG, "Directory not created");
    }
    return file;
  }

  private String findNumber(String rawOCRResult) {
    String numberResult = "";
    List<String> possibleNumberMatches = new ArrayList<String>();
    Matcher numberMatcher = Pattern.compile("[0-9]{4,6}").matcher(rawOCRResult);

    while (numberMatcher.find()) {
      possibleNumberMatches.add(numberMatcher.group());
    }
    if (possibleNumberMatches.size() < 1) {
      numberResult += "No number\n";
    } else if (possibleNumberMatches.size() == 1) {
      numberResult += possibleNumberMatches.get(0);
    } else if (possibleNumberMatches.size() > 1) {
      numberResult += "multiple possible number matches: \n";
      for (String possibleNumber : possibleNumberMatches) {
        numberResult += possibleNumber + "\n";
      }
    }
    return numberResult;
  }

  private String findIntial(String rawOCRResult) {
    List<String> possibleInitialMatches = new ArrayList<String>();
    Matcher initialMatcher = Pattern.compile("[A-Z]{2,4}").matcher(rawOCRResult);

    while (initialMatcher.find()) {
      possibleInitialMatches.add(initialMatcher.group());
    }
    String initialResult = "";

    if (possibleInitialMatches.size() < 1) {
      initialResult += "No Initial\n";
    } else if (possibleInitialMatches.size() == 1) {
      initialResult += possibleInitialMatches.get(0) + " ";
    } else if (possibleInitialMatches.size() > 1) {
      initialResult += "multiple possible initial matches: \n";
      for (String possibleInitial : possibleInitialMatches) {
        initialResult += possibleInitial + "\n";
      }
    }
    return initialResult;
  }

}
