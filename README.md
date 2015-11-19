virtual-star-tracker
====================

Emulate a fancy long exposure motorized camera mount with star tracking and hugin.

<figure>
  <img width="45%" src="https://github.com/salamanders/virtual-star-tracker/blob/master/example/sum.png" alt="Example of combined images">
  <figcaption>Left hand side is combined images, right hand is a single one of the source images</figcaption>
</figure>

## Setup

1. Take more than 100 pictures of the night sky from a fixed camera
1. Install Hugin from http://hugin.sourceforge.net/
1. Lots of time to let the computer think.

## Run 

1. Execute FindStars to create raw.pto in the target folder
1. cd to the folder
1. Clean the file /Applications/Hugin/Hugin.app/Contents/MacOS/cpclean raw.pto
1. Optimize the file (long wait!) /Applications/Hugin/Hugin.app/Contents/MacOS/autooptimiser -a -s -m -o opt.pto raw_clean.pto
1. Check to make sure it is sane /Applications/Hugin/Hugin.app/Contents/MacOS/checkpto opt.pto
1. Render the images to /Applications/Hugin/Hugin.app/Contents/MacOS/nona -o rendered opt.pto
1. Convert to PNG cd rendered && mogrify -format png *.tif && cd ..
1. Generate sum and median images: LumMedian

## Who could be interested?
* https://groups.yahoo.com/neo/groups/DeepSkyStacker/info
* http://groups.google.com/group/hugin-ptx


## Notes

* [http://wiki.panotools.org/Panorama_scripting_in_a_nutshell#Optimising_positions_and_geometry]
* enblend -o finished.tif out0000.tif out0001.tif out0002.tif
* convert rendered/*.tif -evaluate-sequence median median.png
* convert rendered/*.tif -evaluate-sequence add add.png


/*
 for (final Directory directory : metadata.getDirectories()) {
    for (final Tag tag : directory.getTags())
        System.out.println(tag);
    for (String error : directory.getErrors())
        System.err.println("ERROR: " + error);
}
*/

// Only every X image will be used (in case you took too rapid a burst)
          EVERY_X_IMAGES = 1,
          // Skip this many absolute frames
          TRIM_START = 0,
          // Max frames to allow through (after every x and trim start)
          MAX_FRAMES = 10_000,
          // A trail must be at least this long
          TRAIL_MIN_DIST_SQ = 10,
          // A trail must be at least this duration
          TRAIL_MIN_FRAMES = 10,
          // How many trails intersect in a frame to keep it
          MIN_TRAILS_FOR_USEFUL_FRAME = 5,
          // How often to create a shared control point anchor point
          FRAME_HOP_DROP = 1;