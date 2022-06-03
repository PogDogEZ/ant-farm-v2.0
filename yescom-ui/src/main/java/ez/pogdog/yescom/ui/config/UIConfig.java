package ez.pogdog.yescom.ui.config;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;

import java.util.Arrays;
import java.util.Vector;

public class UIConfig implements IConfig {

    public final ChatConfig chat;
    public final RendererConfig renderer;

    public UIConfig() {
        YesCom.getInstance().configHandler.addConfiguration(this);

        chat = new ChatConfig();
        renderer = new RendererConfig();
    }

    @Override
    public String getIdentifier() {
        return "ui";
    }

    @Override
    public IConfig getParent() {
        return YesCom.getInstance();
    }

    /* ------------------------------ Classes ------------------------------ */

    /**
     * Configuration options for the chat tab.
     */
    public class ChatConfig implements IConfig {

        public final Option<Integer> MAX_LINES = new Option<>(
                "Max lines",
                "The maximum number of chat lines to store.",
                150
        );

        /* ------------------------------ Colours ------------------------------ */

        // These colours were definitely NOT stolen from Gnome terminal light mode
        public final Option<Vector<Integer>> BLACK_COLOUR = new Option<>(
                "Black colour",
                "The black colour to use for chat messages.",
                new Vector<>(Arrays.asList(23, 20, 33))
        );
        public final Option<Vector<Integer>> DARK_BLUE_COLOUR = new Option<>(
                "Dark blue colour",
                "The dark blue colour to use for chat messages.",
                new Vector<>(Arrays.asList(18, 72, 139))
        );
        public final Option<Vector<Integer>> DARK_GREEN_COLOUR = new Option<>(
                "Dark green colour",
                "The dark green colour to use for chat messages.",
                new Vector<>(Arrays.asList(38, 162, 105))
        );
        public final Option<Vector<Integer>> DARK_AQUA_COLOUR = new Option<>(
                "Dark aqua colour",
                "The dark aqua colour to use for chat messages.",
                new Vector<>(Arrays.asList(42, 161, 179))
        );
        public final Option<Vector<Integer>> DARK_RED_COLOUR = new Option<>(
                "Dark red colour",
                "The dark red colour to use for chat messages.",
                new Vector<>(Arrays.asList(192, 28, 40))
        );
        public final Option<Vector<Integer>> DARK_PURPLE_COLOUR = new Option<>(
                "Dark purple colour",
                "The dark purple colour to use for chat messages.",
                new Vector<>(Arrays.asList(163, 71, 186))
        );
        public final Option<Vector<Integer>> GOLD_COLOUR = new Option<>(
                "Gold colour",
                "The gold colour to use for chat messages.",
                new Vector<>(Arrays.asList(162, 115, 76))
        );
        public final Option<Vector<Integer>> GRAY_COLOUR = new Option<>(
                "Gray colour",
                "The gray colour to use for chat messages.",
                new Vector<>(Arrays.asList(208, 207, 204))
        );
        public final Option<Vector<Integer>> DARK_GRAY_COLOUR = new Option<>(
                "Dark gray colour",
                "The dark gray colour to use for chat messages.",
                new Vector<>(Arrays.asList(94, 92, 100))
        );
        public final Option<Vector<Integer>> BLUE_COLOUR = new Option<>(
                "Blue colour",
                "The blue colour to use for chat messages.",
                new Vector<>(Arrays.asList(42, 123, 222))
        );
        public final Option<Vector<Integer>> GREEN_COLOUR = new Option<>(
                "Green colour",
                "The green colour to use for chat messages.",
                new Vector<>(Arrays.asList(51, 209, 122))
        );
        public final Option<Vector<Integer>> AQUA_COLOUR = new Option<>(
                "Aqua colour",
                "The aqua colour to use for chat messages.",
                new Vector<>(Arrays.asList(51, 199, 222))
        );
        public final Option<Vector<Integer>> RED_COLOUR = new Option<>(
                "Red colour",
                "The red colour to use for chat messages.",
                new Vector<>(Arrays.asList(246, 97, 81))
        );
        public final Option<Vector<Integer>> PURPLE_COLOUR = new Option<>(
                "Purple colour",
                "The purple colour to use for chat messages.",
                new Vector<>(Arrays.asList(192, 97, 203))
        );
        public final Option<Vector<Integer>> YELLOW_COLOUR = new Option<>(
                "Yellow colour",
                "The yellow colour to use for chat messages.",
                new Vector<>(Arrays.asList(233, 173, 12))
        );
        public final Option<Vector<Integer>> WHITE_COLOUR = new Option<>(
                "White colour",
                "The white colour to use for chat messages.",
                new Vector<>(Arrays.asList(255, 255, 255))
        );

        public ChatConfig() {
            YesCom.getInstance().configHandler.addConfiguration(this);
        }

        @Override
        public String getIdentifier() {
            return "chat";
        }

        @Override
        public IConfig getParent() {
            return UIConfig.this;
        }
    }

    /**
     * Configuration options for the grid view's renderer.
     */
    public class RendererConfig implements IConfig {

        /* ------------------------------ Scaling ------------------------------ */

        public final Option<Integer> SCALE_SENSITIVITY = new Option<>(
                "Scale sensitivity",
                "The sensitivity when scaling via scrolling.",
                1000
        );

        /* ------------------------------ Colours ------------------------------ */

        public final Option<Vector<Integer>> SELECTION_COLOUR = new Option<>(
                "Selection colour",
                "The colour of the selection boxes (RGB).",
                new Vector<>(Arrays.asList(0, 0, 255))
        );
        public final Option<Vector<Integer>> CHUNK_GRID_COLOUR = new Option<>(
                "Chunk grid colour",
                "The colour of the chunk grid that appears when you zoom in.",
                new Vector<>(Arrays.asList(159, 159, 159))
        );
        public final Option<Vector<Integer>> HIGHWAY_COLOUR = new Option<>(
                "Highway colour",
                "The colour of the highways (RGB).",
                new Vector<>(Arrays.asList(150, 150, 150))
        );
        public final Option<Vector<Integer>> SCALE_INDICATOR_COLOUR = new Option<>(
                "Scale indicator colour",
                "The colour of the scale indicator in the bottom left corner (RGB).",
                new Vector<>(Arrays.asList(119, 119, 119))
        );

        public RendererConfig() {
            YesCom.getInstance().configHandler.addConfiguration(this);
        }

        @Override
        public String getIdentifier() {
            return "renderer";
        }

        @Override
        public IConfig getParent() {
            return UIConfig.this;
        }
    }
}
