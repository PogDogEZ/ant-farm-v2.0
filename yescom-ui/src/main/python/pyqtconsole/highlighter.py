from PyQt6.QtCore import *
from PyQt6.QtGui import *

import keyword


def format(color, style=''):
    """Return a QTextCharFormat with the given attributes.
    """
    _color = QColor()
    _color.setNamedColor(color)

    _format = QTextCharFormat()
    _format.setForeground(_color)
    if 'bold' in style:
        _format.setFontWeight(QFont.Weight.Bold)
    if 'italic' in style:
        _format.setFontItalic(True)

    return _format


# Syntax styles that can be shared by all languages
STYLES = {
    'keyword': format('blue', 'bold'),
    'operator': format('red'),
    'brace': format('darkGray'),
    'defclass': format('black', 'bold'),
    'string': format('magenta'),
    'string2': format('darkMagenta'),
    'comment': format('darkGreen', 'italic'),
    'self': format('black', 'italic'),
    'numbers': format('brown'),
    'inprompt': format('darkBlue', 'bold'),
    'outprompt': format('darkRed', 'bold'),
}


class PromptHighlighter(object):

    def __init__(self, formats=None):
        self.styles = styles = dict(STYLES, **(formats or {}))
        self.rules = [
            # Match the prompt incase of a console
            (QRegularExpression(r'IN[^\:]*'), 0, styles['inprompt']),
            (QRegularExpression(r'OUT[^\:]*'), 0, styles['outprompt']),
            # Numeric literals
            (QRegularExpression(r'\b[+-]?[0-9]+\b'), 0, styles['numbers']),
        ]

    def highlight(self, text):
        for expression, nth, format in self.rules:
            captured = expression.match(text, 0).capturedTexts()
            offset = 0
            for text_ in captured:
                index = text.index(text_, offset)
                yield index, len(text_), format
                offset += len(text_)


class PythonHighlighter(QSyntaxHighlighter):
    """Syntax highlighter for the Python language.
    """
    # Python keywords
    keywords = keyword.kwlist

    def __init__(self, document, formats=None):
        QSyntaxHighlighter.__init__(self, document)

        self.styles = styles = dict(STYLES, **(formats or {}))

        # Multi-line strings (expression, flag, style)
        # FIXME: The triple-quotes in these two lines will mess up the
        # syntax highlighting from this point onward
        self.tri_single = (QRegularExpression("'''"), 1, styles['string2'])
        self.tri_double = (QRegularExpression('"""'), 2, styles['string2'])

        rules = []

        # Keyword, operator, and brace rules
        rules += [(r'\b%s\b' % w, 0, styles['keyword'])
                  for w in PythonHighlighter.keywords]

        # All other rules
        rules += [
            # 'self'
            # (r'\bself\b', 0, STYLES['self']),

            # Double-quoted string, possibly containing escape sequences
            (r'"[^"\\]*(\\.[^"\\]*)*"', 0, styles['string']),
            # Single-quoted string, possibly containing escape sequences
            (r"'[^'\\]*(\\.[^'\\]*)*'", 0, styles['string']),

            # 'def' followed by an identifier
            (r'\bdef\b\s*(\w+)', 1, styles['defclass']),
            # 'class' followed by an identifier
            (r'\bclass\b\s*(\w+)', 1, styles['defclass']),

            # From '#' until a newline
            (r'#[^\n]*', 0, styles['comment']),

            # Numeric literals
            (r'\b[+-]?[0-9]+[lL]?\b', 0, styles['numbers']),
            (r'\b[+-]?0[xX][0-9A-Fa-f]+[lL]?\b', 0, styles['numbers']),
            (r'\b[+-]?[0-9]+(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?\b', 0,
             styles['numbers']),
        ]

        # Build a QRegExp for each pattern
        self.rules = [(QRegularExpression(pat), index, fmt)
                      for (pat, index, fmt) in rules]

    def highlightBlock(self, text):
        """Apply syntax highlighting to the given block of text.
        """
        # Do other syntax formatting
        for expression, nth, format in self.rules:
            captured = expression.match(text, 0).capturedTexts()
            offset = 0
            for text_ in captured:
                try:
                    index = text.index(text_, offset)
                    self.setFormat(index, len(text_), format)
                    offset += len(text_)
                except ValueError:
                    ...

        self.setCurrentBlockState(0)

        # Do multi-line strings
        in_multiline = self.match_multiline(text, *self.tri_single)
        if not in_multiline:
            in_multiline = self.match_multiline(text, *self.tri_double)

    def match_multiline(self, text, delimiter, in_state, style):
        """Do highlighting of multi-line strings. ``delimiter`` should be a
        ``QRegExp`` for triple-single-quotes or triple-double-quotes, and
        ``in_state`` should be a unique integer to represent the corresponding
        state changes when inside those strings. Returns True if we're still
        inside a multi-line string when this function is finished.
        """

        # # If inside triple-single quotes, start at 0
        # if self.previousBlockState() == in_state:
        #     start = 0
        #     add = 0
        # # Otherwise, look for the delimiter on this line
        # else:
        #     start = delimiter.indexIn(text)
        #     # Move past this match
        #     add = ...
        #
        # # As long as there's a delimiter match on this line...
        # while start >= 0:
        #     # Look for the ending delimiter
        #     end = delimiter.indexIn(text, start + add)
        #     # Ending delimiter on this line?
        #     if end >= add:
        #         length = end - start + add + delimiter.matchedLength()
        #         self.setCurrentBlockState(0)
        #     # No; multi-line string
        #     else:
        #         self.setCurrentBlockState(in_state)
        #         length = len(text) - start + add
        #     # Apply formatting
        #     self.setFormat(start, length, style)
        #     # Look for the next match
        #     start = delimiter.indexIn(text, start + length)

        # Return True if still inside a multi-line string, False otherwise
        return self.currentBlockState() == in_state
