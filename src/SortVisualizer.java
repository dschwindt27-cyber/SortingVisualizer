import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * SORTING VISUALIZER - run this file (it has the main method).
 *
 * Pick an algorithm, type or generate some data, press "Run Sort", then step
 * through it (or press Play) to watch and hear every comparison, swap and write.
 *
 * (Framework file - students do not need to edit this.)
 */
public class SortVisualizer extends JFrame {

    /** The main algorithms, always shown first and in this order. */
    private static SortAlgorithm[] builtInAlgorithms() {
        return new SortAlgorithm[] {
                new SelectionSort(),
                new InsertionSort(),
                new ShellSort(),
                new MergeSort(),
                new HeapSort(),
                new QuickSort(),
                new MySort()          // the "extra sort" slot for students who finish early
        };
    }

    /**
     * Builds the drop-down list: the built-in sorts, then ANY other class in the
     * project folder that implements SortAlgorithm (found automatically).
     * So to add a new sort, a student just copies MySort.java, renames the file
     * and class, and recompiles - no other file needs to change.
     */
    static List<SortAlgorithm> loadAlgorithms() {
        List<SortAlgorithm> list = new ArrayList<SortAlgorithm>();
        java.util.Set<String> known = new java.util.HashSet<String>();
        for (SortAlgorithm a : builtInAlgorithms()) {
            list.add(a);
            known.add(a.getClass().getName());
        }

        List<SortAlgorithm> extras = new ArrayList<SortAlgorithm>();
        try {
            java.net.URL where = SortVisualizer.class.getProtectionDomain().getCodeSource().getLocation();
            java.io.File dir = new java.io.File(where.toURI());
            String[] files = dir.isDirectory() ? dir.list() : null;   // (inside a .jar: built-ins only)
            if (files != null) {
                for (String f : files) {
                    if (!f.endsWith(".class") || f.contains("$")) continue;
                    String name = f.substring(0, f.length() - 6);
                    if (known.contains(name)) continue;
                    try {
                        Class<?> c = Class.forName(name);
                        if (SortAlgorithm.class.isAssignableFrom(c) && !c.isInterface()
                                && !java.lang.reflect.Modifier.isAbstract(c.getModifiers())) {
                            extras.add((SortAlgorithm) c.getDeclaredConstructor().newInstance());
                        }
                    } catch (Throwable ignored) {
                        // not a sort, or it has no plain constructor: skip it
                    }
                }
            }
        } catch (Throwable ignored) {
            // couldn't look at the folder: just use the built-ins
        }
        Collections.sort(extras, new java.util.Comparator<SortAlgorithm>() {
            public int compare(SortAlgorithm x, SortAlgorithm y) { return x.getName().compareToIgnoreCase(y.getName()); }
        });
        list.addAll(extras);
        return list;
    }

    private static final int MAX_ITEMS = 1000;
    private static final int MAX_STEPS = 4000000;
    private static final long TIMEOUT_MS = 3000;
    private static final String[] PATTERNS = { "Random", "Already sorted", "Reversed", "Nearly sorted", "Few unique values" };

    // ---- controls
    private final JComboBox<AlgoChoice> algoBox = new JComboBox<AlgoChoice>();
    private final JRadioButton numbersBtn = new JRadioButton("Numbers", true);
    private final JRadioButton charsBtn = new JRadioButton("Characters");
    private final JTextField dataField = new JTextField(40);
    private final JComboBox<String> patternBox = new JComboBox<String>(PATTERNS);
    private final JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(16, 2, MAX_ITEMS, 1));
    private final JButton generateBtn = new JButton("Generate");
    private final JButton runBtn = new JButton("Run Sort");

    private final BarPanel bars = new BarPanel();

    private final JButton startBtn = new JButton("|<  Start");
    private final JButton backBtn = new JButton("<  Back");
    private final JButton playBtn = new JButton("Play");
    private final JButton stepBtn = new JButton("Step  >");
    private final JButton endBtn = new JButton("End  >|");
    private final JSlider speedSlider = new JSlider(0, 100, 45);
    private final JCheckBox soundBox = new JCheckBox("Sound (comparisons)", true);
    private final JButton traceBtn = new JButton("Trace Table");
    private final JButton stopSoundBtn = new JButton("Stop Sound");

    private final JLabel statsLabel = new JLabel(" ");
    private final JLabel messageLabel = new JLabel(" ");
    private final JLabel resultLabel = new JLabel(" ");

    private final DefaultTableModel historyModel = new DefaultTableModel(
            new String[] { "#", "Algorithm", "n", "Data", "Compares", "Swaps", "Writes", "Result" }, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };

    // ---- state
    private final TonePlayer tones = new TonePlayer();
    private final Timer timer;
    private List<Step> steps = new ArrayList<Step>();
    private int index = 0;
    private double minKey = 0, maxKey = 1;
    private boolean finishedCorrectly = false;
    private String lastGeneratedText = "";
    private String lastGeneratedPattern = "";
    private int runCount = 0;
    private final Random rng = new Random();
    private TraceWindow traceWindow;          // created the first time it's opened
    private String traceAlgName = "";
    private String traceSummary = "";

    public SortVisualizer() {
        super("Sorting Algorithm Visualizer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        List<SortAlgorithm> algs = loadAlgorithms();
        java.util.Map<String, Integer> nameCount = new java.util.HashMap<String, Integer>();
        for (SortAlgorithm a : algs) {
            Integer c = nameCount.get(a.getName());
            nameCount.put(a.getName(), c == null ? 1 : c + 1);
        }
        for (SortAlgorithm a : algs) {
            // two classes with the same getName()? show the class name too so students can tell them apart
            boolean dup = nameCount.get(a.getName()) > 1;
            algoBox.addItem(new AlgoChoice(a, dup));
        }
        ButtonGroup group = new ButtonGroup();
        group.add(numbersBtn);
        group.add(charsBtn);

        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(new EmptyBorder(8, 8, 8, 8));
        main.add(buildTop(), BorderLayout.NORTH);
        main.add(bars, BorderLayout.CENTER);
        main.add(buildBottom(), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, main, buildHistory());
        split.setResizeWeight(0.75);
        setContentPane(split);

        timer = new Timer(50, new ActionListener() {
            public void actionPerformed(ActionEvent e) { tick(); }
        });

        wireEvents();
        generateData();
        updateSpeed();

        setSize(1300, 760);
        setLocationRelativeTo(null);
        split.setDividerLocation(0.74);

        if (!tones.isAvailable()) {
            soundBox.setSelected(false);
            soundBox.setEnabled(false);
            soundBox.setText("Sound (no audio device)");
            stopSoundBtn.setEnabled(false);
        }
    }

    // ================================================================ layout

    private JPanel buildTop() {
        JPanel top = new JPanel(new GridLayout(2, 1, 4, 4));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row1.add(new JLabel("Algorithm:"));
        row1.add(algoBox);
        row1.add(Box.createHorizontalStrut(20));
        row1.add(new JLabel("Data type:"));
        row1.add(numbersBtn);
        row1.add(charsBtn);
        row1.add(Box.createHorizontalStrut(20));
        row1.add(new JLabel("Pattern:"));
        row1.add(patternBox);
        row1.add(new JLabel("Size:"));
        row1.add(sizeSpinner);
        row1.add(generateBtn);

        JPanel row2 = new JPanel(new BorderLayout(8, 0));
        row2.setBorder(new EmptyBorder(0, 8, 0, 8));
        row2.add(new JLabel("Data (edit me!):"), BorderLayout.WEST);
        dataField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        dataField.setToolTipText("Numbers: separate with commas or spaces, e.g. 5, 3, 9, 1.   "
                + "Characters: e.g. S O R T E X A M P L E  or just SORTEXAMPLE");
        row2.add(dataField, BorderLayout.CENTER);
        runBtn.setFont(runBtn.getFont().deriveFont(Font.BOLD, 14f));
        row2.add(runBtn, BorderLayout.EAST);

        top.add(row1);
        top.add(row2);
        return top;
    }

    private JPanel buildBottom() {
        JPanel bottom = new JPanel(new GridLayout(4, 1, 2, 2));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        playBtn.setPreferredSize(new Dimension(90, playBtn.getPreferredSize().height));
        controls.add(startBtn);
        controls.add(backBtn);
        controls.add(playBtn);
        controls.add(stepBtn);
        controls.add(endBtn);
        controls.add(Box.createHorizontalStrut(20));
        controls.add(new JLabel("Slow"));
        speedSlider.setPreferredSize(new Dimension(180, speedSlider.getPreferredSize().height));
        controls.add(speedSlider);
        controls.add(new JLabel("Fast"));
        controls.add(Box.createHorizontalStrut(20));
        controls.add(soundBox);
        stopSoundBtn.setToolTipText("Silence all sound right now (the animation keeps going)");
        stopSoundBtn.setForeground(new Color(180, 30, 30));
        stopSoundBtn.setFont(stopSoundBtn.getFont().deriveFont(Font.BOLD));
        controls.add(stopSoundBtn);
        controls.add(Box.createHorizontalStrut(20));
        traceBtn.setFont(traceBtn.getFont().deriveFont(Font.BOLD));
        controls.add(traceBtn);

        statsLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        statsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        messageLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        resultLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        resultLabel.setHorizontalAlignment(SwingConstants.CENTER);

        bottom.add(controls);
        bottom.add(statsLabel);
        bottom.add(messageLabel);
        bottom.add(resultLabel);
        return bottom;
    }

    private JPanel buildHistory() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(new TitledBorder("Run history (for your analysis)"));
        JTable table = new JTable(historyModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = { 30, 110, 35, 110, 70, 55, 55, 80 };
        for (int c = 0; c < widths.length; c++) table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        p.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton copy = new JButton("Copy as CSV");
        JButton clear = new JButton("Clear");
        copy.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { copyHistoryCsv(); }
        });
        clear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { historyModel.setRowCount(0); runCount = 0; }
        });
        buttons.add(copy);
        buttons.add(clear);
        p.add(buttons, BorderLayout.SOUTH);
        p.setPreferredSize(new Dimension(320, 400));
        return p;
    }

    private void wireEvents() {
        generateBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { generateData(); }
        });
        ActionListener regen = new ActionListener() {
            public void actionPerformed(ActionEvent e) { generateData(); }
        };
        numbersBtn.addActionListener(regen);
        charsBtn.addActionListener(regen);
        patternBox.addActionListener(regen);
        dataField.addActionListener(new ActionListener() {  // pressing Enter in the box
            public void actionPerformed(ActionEvent e) { previewData(); }
        });
        algoBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { previewData(); }
        });
        runBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { runSort(); }
        });
        startBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { pause(); goTo(0, false); }
        });
        backBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { pause(); goTo(index - 1, true); }
        });
        stepBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { pause(); goTo(index + 1, true); }
        });
        endBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { pause(); goTo(steps.size() - 1, false); }
        });
        playBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { togglePlay(); }
        });
        soundBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { if (!soundBox.isSelected()) tones.stopAll(); }
        });
        stopSoundBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { tones.stopAll(); }
        });
        traceBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { openTraceWindow(); }
        });
        speedSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent e) {
                updateSpeed();
                // changing speed mid-animation can leave old notes queued: clear them
                if (timer.isRunning()) tones.stopAll();
            }
        });
    }

    // ================================================================ data input

    private void generateData() {
        int n = (Integer) sizeSpinner.getValue();
        String pattern = (String) patternBox.getSelectedItem();
        boolean chars = charsBtn.isSelected();

        int[] v = new int[n];
        for (int k = 0; k < n; k++) {
            if (pattern.equals("Few unique values")) v[k] = 1 + rng.nextInt(4);
            else v[k] = chars ? rng.nextInt(26) : 1 + rng.nextInt(99);
        }
        if (pattern.equals("Already sorted") || pattern.equals("Reversed") || pattern.equals("Nearly sorted")) {
            java.util.Arrays.sort(v);
        }
        if (pattern.equals("Reversed")) {
            for (int a = 0, b = n - 1; a < b; a++, b--) { int t = v[a]; v[a] = v[b]; v[b] = t; }
        }
        if (pattern.equals("Nearly sorted")) {
            for (int s = 0; s < Math.max(1, n / 10); s++) {
                int a = rng.nextInt(n), b = Math.min(n - 1, a + 1 + rng.nextInt(2));
                int t = v[a]; v[a] = v[b]; v[b] = t;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < n; k++) {
            if (k > 0) sb.append(chars ? " " : ", ");
            if (chars) {
                int letter = pattern.equals("Few unique values") ? v[k] - 1 : v[k];
                sb.append((char) ('A' + letter));
            } else {
                sb.append(pattern.equals("Few unique values") ? v[k] * 10 : v[k]);
            }
        }
        dataField.setText(sb.toString());
        lastGeneratedText = sb.toString();
        lastGeneratedPattern = pattern;
        previewData();
    }

    /** Show the unsorted data so students can see it before running. */
    private void previewData() {
        pause();
        try {
            Item[] items = parseInput();
            setRange(items);
            steps = new ArrayList<Step>(Collections.singletonList(Step.start(items)));
            index = 0;
            finishedCorrectly = false;
            resultLabel.setForeground(Color.DARK_GRAY);
            resultLabel.setText("Press \"Run Sort\" to record "
                    + ((AlgoChoice) algoBox.getSelectedItem()).alg.getName() + " on this data.");
            traceAlgName = ((AlgoChoice) algoBox.getSelectedItem()).alg.getName();
            traceSummary = "Not run yet. Data: " + dataField.getText().trim();
            updateTraceSteps();
            refresh();
        } catch (IllegalArgumentException ex) {
            resultLabel.setForeground(new Color(190, 40, 40));
            resultLabel.setText(ex.getMessage());
        }
    }

    private Item[] parseInput() {
        String text = dataField.getText().trim();
        List<Item> list = new ArrayList<Item>();
        if (charsBtn.isSelected()) {
            for (char c : text.toCharArray()) {
                if (Character.isWhitespace(c) || c == ',') continue;
                list.add(new Item(c, String.valueOf(c), list.size()));
            }
        } else {
            for (String tok : text.split("[,\\s]+")) {
                if (tok.isEmpty()) continue;
                double d;
                try {
                    d = Double.parseDouble(tok);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("\"" + tok + "\" is not a number. "
                            + "Choose the Characters data type to sort letters.");
                }
                String label = (d == Math.rint(d) && Math.abs(d) < 1e15) ? String.valueOf((long) d) : tok;
                list.add(new Item(d, label, list.size()));
            }
        }
        if (list.size() < 2) throw new IllegalArgumentException("Enter at least 2 values to sort.");
        if (list.size() > MAX_ITEMS) throw new IllegalArgumentException(
                "Please use at most " + MAX_ITEMS + " values (you entered " + list.size() + ").");
        return list.toArray(new Item[0]);
    }

    private void setRange(Item[] items) {
        minKey = Double.MAX_VALUE;
        maxKey = -Double.MAX_VALUE;
        for (Item it : items) {
            minKey = Math.min(minKey, it.key());
            maxKey = Math.max(maxKey, it.key());
        }
    }

    // ================================================================ running the student's sort

    private void runSort() {
        pause();
        final Item[] items;
        try {
            items = parseInput();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Check your data", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final SortAlgorithm alg = ((AlgoChoice) algoBox.getSelectedItem()).alg;
        final String dataDesc = describeData(items.length);
        setRange(items);
        runBtn.setEnabled(false);
        resultLabel.setForeground(Color.DARK_GRAY);
        resultLabel.setText("Running " + alg.getName() + "...");

        new SwingWorker<SortRunner.Result, Void>() {
            @Override protected SortRunner.Result doInBackground() {
                return SortRunner.run(alg, items, MAX_STEPS, TIMEOUT_MS);
            }
            @Override protected void done() {
                runBtn.setEnabled(true);
                try {
                    showResult(alg, items.length, dataDesc, get());
                } catch (Exception ex) {
                    resultLabel.setForeground(new Color(190, 40, 40));
                    resultLabel.setText("Something went wrong: " + ex);
                }
            }
        }.execute();
    }

    private String describeData(int n) {
        String type = charsBtn.isSelected() ? "chars" : "nums";
        if (dataField.getText().equals(lastGeneratedText)) return lastGeneratedPattern + " " + type;
        return "Custom " + type;
    }

    private void showResult(SortAlgorithm alg, int n, String dataDesc, SortRunner.Result r) {
        steps = r.steps;
        index = 0;
        finishedCorrectly = r.error == null && r.sorted && r.sameItems;

        String verdict;
        Color color;
        if (r.error != null) {
            verdict = "Error";
            color = new Color(190, 40, 40);
            resultLabel.setText("<html><center>" + escape(r.error)
                    + "<br>You can still step through what happened before the problem.</center></html>");
        } else if (!r.sameItems) {
            verdict = "Lost values";
            color = new Color(190, 40, 40);
            resultLabel.setText("Not correct: some values were lost or copied twice. Check your set() calls.");
        } else if (!r.sorted) {
            verdict = "Not sorted";
            color = new Color(200, 110, 0);
            if (r.comparisons == 0 && r.swaps == 0 && r.writes == 0) {
                resultLabel.setText("Nothing happened - has " + alg.getName() + " been written yet? Open "
                        + alg.getClass().getSimpleName() + ".java and get coding!");
            } else {
                resultLabel.setText("Not sorted yet: the list isn't in order at the end. Step through to find where it goes wrong.");
            }
        } else {
            verdict = "Correct";
            color = new Color(30, 140, 60);
            String stable = r.stable == null ? "(no duplicate values, so stability not tested)"
                    : (r.stable ? "Stable on this data: YES" : "Stable on this data: NO");
            resultLabel.setText("Sorted correctly!  " + n + " items:  " + r.comparisons + " comparisons, "
                    + r.swaps + " swaps, " + r.writes + " writes.   " + stable);
        }
        resultLabel.setForeground(color);

        runCount++;
        historyModel.addRow(new Object[] { runCount, alg.getName(), n, dataDesc,
                r.comparisons, r.swaps, r.writes, verdict });

        traceAlgName = alg.getName();
        traceSummary = "Data (" + n + " items): " + dataField.getText().trim()
                + "   |   Result: " + verdict + "   |   " + r.comparisons + " comparisons, "
                + r.swaps + " swaps, " + r.writes + " writes"
                + (r.error != null ? "   |   " + r.error : "")
                + (r.stable != null ? "   |   Stable on this data: " + (r.stable ? "yes" : "no") : "");
        updateTraceSteps();
        refresh();
    }

    // ================================================================ stepping & playback

    private void goTo(int newIndex, boolean withSound) {
        if (steps.isEmpty()) return;
        newIndex = Math.max(0, Math.min(steps.size() - 1, newIndex));
        int oldIndex = index;
        index = newIndex;
        refresh();
        if (withSound && newIndex != oldIndex) playSoundFor(oldIndex, newIndex);
    }

    private void togglePlay() {
        if (timer.isRunning()) {
            pause();
        } else {
            if (index >= steps.size() - 1) index = 0;  // replay from the beginning
            timer.start();
            playBtn.setText("Pause");
        }
    }

    /** Stop playback AND silence any sound still waiting to be played. */
    private void pause() {
        if (timer != null && timer.isRunning()) timer.stop();
        playBtn.setText("Play");
        if (tones != null) tones.stopAll();
    }

    private int stepsPerTick = 1;

    private void updateSpeed() {
        // slider 0 -> 1 second per step ... slider 100 -> about 3 ms per step
        double delay = 1000 * Math.pow(0.003, speedSlider.getValue() / 100.0);
        if (delay < 15) {
            stepsPerTick = (int) Math.ceil(15 / delay);
            timer.setDelay(15);
        } else {
            stepsPerTick = 1;
            timer.setDelay((int) delay);
        }
    }

    private void tick() {
        if (index >= steps.size() - 1) {
            pause();
            return;
        }
        goTo(index + stepsPerTick, true);
        if (index >= steps.size() - 1) {
            // reached the end: stop the timer but don't cut off the final sweep
            timer.stop();
            playBtn.setText("Play");
        }
    }

    /**
     * Sound is played ONLY for comparisons. The two values being compared are
     * played as two quick notes (left value first, then right value).
     * When playback jumps several steps at once, the latest comparison in that
     * jump is the one you hear.
     */
    private void playSoundFor(int oldIndex, int newIndex) {
        if (!soundBox.isSelected()) return;
        Step last = steps.get(newIndex);
        if (last.type == Step.Type.DONE && finishedCorrectly && newIndex > oldIndex) {
            double[] f = new double[last.snapshot.length];
            for (int k = 0; k < f.length; k++) f[k] = TonePlayer.frequencyFor(last.snapshot[k].key(), minKey, maxKey);
            tones.stopAll();   // drop any leftover comparison notes first
            tones.sweep(f, Math.max(15, 600 / f.length));
            return;
        }

        Step compare = null;
        if (newIndex > oldIndex) {
            for (int k = newIndex; k > oldIndex; k--) {
                if (steps.get(k).type == Step.Type.COMPARE) { compare = steps.get(k); break; }
            }
        } else if (last.type == Step.Type.COMPARE) {
            compare = last;   // stepping backward onto a comparison
        }
        if (compare == null) return;   // swaps, writes and marks are silent

        double[] f = new double[compare.soundKeys.length];
        for (int k = 0; k < f.length; k++) f[k] = TonePlayer.frequencyFor(compare.soundKeys[k], minKey, maxKey);
        boolean playing = timer.isRunning();
        // Both notes must fit inside the time until the next tick, or sound piles up.
        // (One tick may advance several steps, but only ONE comparison is played per tick.)
        int total = playing ? (int) Math.max(30, Math.min(320, timer.getDelay() * 0.8)) : 320;
        tones.playInOrder(f, total / f.length);
    }

    // ================================================================ trace table window

    private void openTraceWindow() {
        if (traceWindow == null) {
            traceWindow = new TraceWindow(new TraceWindow.JumpListener() {
                public void jumpTo(int stepIndex) { pause(); goTo(stepIndex, true); }
            });
            Point p = getLocation();
            traceWindow.setLocation(p.x + 40, p.y + getHeight() / 3);
        }
        traceWindow.setSteps(steps, traceAlgName, traceSummary);
        traceWindow.setCurrent(index);
        traceWindow.setVisible(true);
        traceWindow.toFront();
    }

    private void updateTraceSteps() {
        if (traceWindow != null && traceWindow.isVisible()) {
            traceWindow.setSteps(steps, traceAlgName, traceSummary);
        }
    }

    private void refresh() {
        if (steps.isEmpty()) return;
        Step s = steps.get(index);
        boolean atEnd = index == steps.size() - 1;
        bars.show(s, minKey, maxKey, atEnd && finishedCorrectly && s.type == Step.Type.DONE);

        statsLabel.setText("Step " + index + " / " + (steps.size() - 1)
                + "        Comparisons: " + s.comparisons
                + "        Swaps: " + s.swaps
                + "        Writes: " + s.writes);
        messageLabel.setText(s.message);
        if (traceWindow != null && traceWindow.isVisible()) traceWindow.setCurrent(index);

        startBtn.setEnabled(index > 0);
        backBtn.setEnabled(index > 0);
        stepBtn.setEnabled(!atEnd);
        endBtn.setEnabled(!atEnd);
        playBtn.setEnabled(steps.size() > 1);
    }

    // ================================================================ helpers

    private void copyHistoryCsv() {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < historyModel.getColumnCount(); c++) {
            if (c > 0) sb.append(',');
            sb.append(historyModel.getColumnName(c));
        }
        sb.append('\n');
        for (int r = 0; r < historyModel.getRowCount(); r++) {
            for (int c = 0; c < historyModel.getColumnCount(); c++) {
                if (c > 0) sb.append(',');
                sb.append(historyModel.getValueAt(r, c));
            }
            sb.append('\n');
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
        JOptionPane.showMessageDialog(this, "Copied! Paste it into a spreadsheet to graph your results.");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class AlgoChoice {
        final SortAlgorithm alg;
        final boolean showClass;
        AlgoChoice(SortAlgorithm alg, boolean showClass) { this.alg = alg; this.showClass = showClass; }
        @Override public String toString() {
            return showClass ? alg.getName() + "  (" + alg.getClass().getSimpleName() + ".java)" : alg.getName();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) { }
                new SortVisualizer().setVisible(true);
            }
        });
    }
}
