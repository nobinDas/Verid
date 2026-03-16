INSERT INTO categories (name, type, icon, color) VALUES
    ('Food',          'expense', 'utensils',     '#f97316'),
    ('Transport',     'expense', 'car',           '#3b82f6'),
    ('Utilities',     'expense', 'bolt',          '#eab308'),
    ('Shopping',      'expense', 'shopping-bag',  '#ec4899'),
    ('Health',        'expense', 'heart',          '#ef4444'),
    ('Entertainment', 'expense', 'film',           '#a855f7'),
    ('Transfer',      'neutral', 'arrows',         '#6b7280'),
    ('Other',         'expense', 'tag',            '#9ca3af')
ON CONFLICT (name) DO NOTHING;
