package ez.pogdog.yescom.core.threads;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.ITickable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Async ticking for faster tasks.
 */
public class FastAsyncUpdater extends Thread {

    private final Logger logger = Logging.getLogger("yescom.core.threads");
    private final YesCom yesCom = YesCom.getInstance();

    public final List<ITickable> tickables = new ArrayList<>();

    public FastAsyncUpdater() {
        setName("yescom-fast-async-updater");
    }

    @Override
    public void run() {
        logger.finest("Starting fast async updater...");
        while (yesCom.isRunning()) {
            long start = System.currentTimeMillis();

            for (ITickable tickable : tickables) tickable.tick();

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed < 50) {
                try {
                    Thread.sleep(50 - elapsed);
                } catch (InterruptedException ignored) {
                }
            } else if (elapsed > 50) {
                logger.warning(String.format("Fast async tick took %dms!", elapsed));
            }
        }
    }
}
