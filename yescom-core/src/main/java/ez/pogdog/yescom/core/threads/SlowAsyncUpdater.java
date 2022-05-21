package ez.pogdog.yescom.core.threads;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.ITickable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Async updater for slower tasks.
 */
public class SlowAsyncUpdater extends Thread {

    private final Logger logger = Logging.getLogger("yescom.core.threads");
    private final YesCom yesCom = YesCom.getInstance();

    public final List<ITickable> tickables = new ArrayList<>();

    public SlowAsyncUpdater() {
        setName("yescom-slow-async-updater");
    }

    @Override
    public void run() {
        logger.finest("Starting slow async updater...");
        while (yesCom.isRunning()) {
            long start = System.currentTimeMillis();

            for (ITickable tickable : tickables) tickable.tick();

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed < 2500) {
                try {
                    Thread.sleep(2500 - elapsed);
                } catch (InterruptedException ignored) {
                }
            } else if (elapsed > 2500) {
                logger.warning(String.format("Slow async tick took %dms!", elapsed));
            }
        }
    }
}
