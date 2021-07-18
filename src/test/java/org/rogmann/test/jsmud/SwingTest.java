package org.rogmann.test.jsmud;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class SwingTest extends JPanel implements ActionListener {
	/** Serialization-id */
	private static final long serialVersionUID = 1L;

	protected final JButton button1;
	protected final JButton button2;
	protected final JTextField textfield;
	
	public SwingTest() {
		button1 = new JButton("Test-Button 1");
        button1.setVerticalTextPosition(AbstractButton.CENTER);
        button1.setHorizontalTextPosition(AbstractButton.LEADING);
        button1.setActionCommand("Button 1");
        button1.addActionListener(this);
        add(button1);

        button2 = new JButton("Test-Button 2");
        button2.setVerticalTextPosition(AbstractButton.CENTER);
        button2.setHorizontalTextPosition(AbstractButton.LEADING);
        button2.setActionCommand("Button 2");
        button2.addActionListener(this);
        add(button2);
        
        textfield = new JTextField("[...]", 20);
        add(textfield);
	}

	public static void main(final String[] args) {
		 SwingUtilities.invokeLater(new Runnable() {
            @Override
			public void run() {
                final JFrame frame = new JFrame("SwingDemo");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         
                SwingTest newContentPane = new SwingTest();
                newContentPane.setOpaque(true); //content panes must be opaque
                frame.setContentPane(newContentPane);

                frame.setMinimumSize(new Dimension(400, 200));
                frame.pack();
                frame.setVisible(true);
            }
        });
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		textfield.setText("Action: " + e.getActionCommand());
	}
}
