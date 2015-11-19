    int finalId = 0;
    for (final Frame frame : allFrames) {
      int usages = 0;

      usages = trails.stream().filter((trail) -> (trail.containsFrame(frame))).map((_item) -> 1).reduce(usages,
              Integer::sum);
      frame.setUseful(usages >= MIN_TRAILS_FOR_USEFUL_FRAME);
      if (frame.isUseful()) {
        frame.setFinalId(finalId);
        finalId++;
      }
    }
    LOG.log(Level.INFO, "Final highest ID:{0}", finalId);

    try (final PrintWriter pto = new PrintWriter(rootDirectory.resolve("raw.pto").toFile());
            final PrintWriter pto2 = new PrintWriter(rootDirectory.resolve("data.tsv").toFile())) {

      pto2.format("%s\t%s\t%s\t%s%n", "TRAIL_ID", "FRAME_ID", "X", "Y");

      pto.println("# hugin project file");
      pto.println("#hugin_ptoversion 2");

      pto.println("p f3 w6000 v170  E1.97085 R0 n\"TIFF\""); // r:CROP - shifts the frames, bad
      pto.println("m g1 i7 f3 m2 p0.00784314\n\n");

      allFrames.stream().filter((frame) -> (frame.isUseful())).forEach((frame) -> {
        // pto.println("#-hugin cropFactor=5.41516");
        if (frame.getFinalId() == 0) {
          pto.format("i w%d h%d f3 v80 Vm5 n\"%s\"%n", dim.width, dim.height, frame.getFile().getPath());
        } else {
          pto.format("i w%d h%d f3 v=0 a=0 b=0 c=0 d=0 e=0 g=0 t=0 Va=0 Vb=0 Vc=0 Vd=0 Vx=0 Vy=0 Vm5 n\"%s\"%n",
                  dim.width, dim.height, frame.getFile().getPath());
        }
      });

      int controlPointCount = 0;
      for (final PixelTrail usefulTrail : trails) {
        for (final Frame frame1 : usefulTrail.getFrames()) {
          if (!frame1.isUseful()) {
            continue;
          }

          pto2.format("%d\t%d\t%d\t%d%n", usefulTrail.getId(), frame1.getFinalId(), usefulTrail.getByFrame(
                  frame1).loc.x, usefulTrail.getByFrame(frame1).loc.y);

          for (final Frame frame2 : usefulTrail.getFrames()) {
            if (!frame2.isUseful()) {
              continue;
            }

            if (frame1.getFinalId() >= frame2.getFinalId()) {
              continue;
            }

						// Todo: more filtering, maybe mod? Better to do by hop distance from last point!
            // Attach to 3 friends, then attach to "waypoints" spread out by hop drop
            // TODO: This should be based on usefulFrames, not all frames
            final int framesDistance = frame2.getFinalId() - frame1.getFinalId();
            if (framesDistance <= 3 || frame1.getFinalId() % FRAME_HOP_DROP == 0 || frame2.getFinalId()
                    % FRAME_HOP_DROP == 0) {
              controlPointCount++;
              pto.format("c n%d N%d x%d y%d X%d Y%d t0%n", frame1.getFinalId(), frame2.getFinalId(), usefulTrail
                      .getByFrame(frame1).loc.x, usefulTrail.getByFrame(frame1).loc.y, usefulTrail.getByFrame(frame2).loc.x,
                      usefulTrail.getByFrame(frame2).loc.y);
            }
          }
        }
      }
      LOG.log(Level.INFO, "Control points:{0}", controlPointCount);