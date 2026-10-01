content = open('main.py').read()
old = 'signal_label = Label('
new = '''disclaimer_label = Label(
            text="Experimental tool - not financial advice. Trade at your own risk.",
            font_size="13sp",
            size_hint=(1, 0.08),
            color=(0.9, 0.6, 0.2, 1)
        )
        signal_label = Label('''

if old in content and 'disclaimer_label' not in content:
    content = content.replace(old, new, 1)
    content = content.replace('layout.add_widget(signal_label)', 'layout.add_widget(disclaimer_label)\n        layout.add_widget(signal_label)')
    open('main.py', 'w').write(content)
    print('Disclaimer added')
else:
    print('Already exists or pattern not found')
