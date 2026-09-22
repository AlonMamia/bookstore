-- Test catalog for filtering, sorting and pagination.
-- PostgreSQL / Flyway migration.
-- Rename V100 if that version is already used in your project.

INSERT INTO books (title, isbn, price, stock_quantity, author, category, version)
VALUES
    ('The Silent Harbor',        '9780000000001',  59.90, 12, 'Maya Stone',       'MYSTERY',         0),
    ('Echoes at Midnight',       '9780000000002',  49.90,  0, 'Maya Stone',       'MYSTERY',         0),
    ('The Last Witness',         '9780000000003',  69.00,  7, 'Daniel Cross',     'MYSTERY',         0),
    ('A Case of Shadows',        '9780000000004',  44.50, 19, 'Daniel Cross',     'MYSTERY',         0),
    ('Murder on Cedar Street',   '9780000000005',  79.90,  3, 'Rachel North',     'MYSTERY',         0),

    ('Kingdom of Ash and Snow',  '9780000000006',  89.90, 15, 'Elena Rivers',     'FANTASY',         0),
    ('The Dragon''s Heir',       '9780000000007',  99.00,  4, 'Elena Rivers',     'FANTASY',         0),
    ('The Glass Crown',          '9780000000008',  74.90,  0, 'Noah Blackwood',   'FANTASY',         0),
    ('Forest of Forgotten Gods', '9780000000009', 109.90,  8, 'Noah Blackwood',   'FANTASY',         0),
    ('The Map of Magic',         '9780000000010',  54.90, 21, 'Lena Hart',        'FANTASY',         0),

    ('Beyond the Red Planet',    '9780000000011',  84.90, 11, 'Alex Morgan',      'SCIENCE_FICTION', 0),
    ('The Quantum Colony',       '9780000000012',  94.50,  6, 'Alex Morgan',      'SCIENCE_FICTION', 0),
    ('Signal from Europa',       '9780000000013',  67.90,  0, 'Iris Chen',        'SCIENCE_FICTION', 0),
    ('Children of the Last Sun', '9780000000014', 119.00,  5, 'Iris Chen',        'SCIENCE_FICTION', 0),
    ('Synthetic Dreams',         '9780000000015',  72.00, 16, 'Owen Reed',        'SCIENCE_FICTION', 0),

    ('Under the Olive Tree',     '9780000000016',  64.90,  9, 'Sarah Bloom',      'FICTION',         0),
    ('A Summer in Jaffa',        '9780000000017',  57.50, 17, 'Sarah Bloom',      'FICTION',         0),
    ('The Long Way Home',        '9780000000018',  76.90,  2, 'Michael Hale',     'FICTION',         0),
    ('Letters Never Sent',       '9780000000019',  42.90,  0, 'Michael Hale',     'FICTION',         0),
    ('Between Two Cities',       '9780000000020',  88.00, 13, 'Amelia Grant',     'FICTION',         0),

    ('The Psychology of Habits', '9780000000021',  96.90, 20, 'Dr. Ethan Cole',   'PSYCHOLOGY',      0),
    ('Understanding Anxiety',    '9780000000022',  82.50,  5, 'Dr. Ethan Cole',   'PSYCHOLOGY',      0),
    ('Mind and Motivation',      '9780000000023',  91.00, 14, 'Dr. Leah Rosen',   'PSYCHOLOGY',      0),
    ('The Social Brain',         '9780000000024', 105.90,  0, 'Dr. Leah Rosen',   'PSYCHOLOGY',      0),
    ('Thinking in Patterns',     '9780000000025',  73.90,  8, 'Samuel Price',     'PSYCHOLOGY',      0),

    ('Modern Java in Practice',  '9780000000026', 149.90, 10, 'Robert Miles',     'TECHNOLOGY',      0),
    ('Spring Boot Foundations',  '9780000000027', 159.00, 18, 'Robert Miles',     'TECHNOLOGY',      0),
    ('Angular at Scale',         '9780000000028', 139.90,  7, 'Nina Patel',       'TECHNOLOGY',      0),
    ('Clean APIs',               '9780000000029', 129.50,  0, 'Nina Patel',       'TECHNOLOGY',      0),
    ('PostgreSQL Deep Dive',     '9780000000030', 169.90,  4, 'Victor Lane',      'TECHNOLOGY',      0),

    ('Empires of the Ancient Sea','9780000000031',112.00,  6, 'Helen Ward',       'HISTORY',         0),
    ('Jerusalem Through Time',   '9780000000032', 124.90, 12, 'Helen Ward',       'HISTORY',         0),
    ('The Silk Road Revisited',  '9780000000033',  98.90,  0, 'David Lin',        'HISTORY',         0),
    ('Europe Between Wars',      '9780000000034', 118.50,  3, 'David Lin',        'HISTORY',         0),
    ('Revolutions That Changed Us','9780000000035',87.90, 15, 'Clara Bennett',    'HISTORY',         0),

    ('Building Better Teams',    '9780000000036',  79.00, 22, 'Marcus Green',     'BUSINESS',        0),
    ('The Focused Founder',      '9780000000037',  92.90,  9, 'Marcus Green',     'BUSINESS',        0),
    ('Strategy Without Noise',   '9780000000038', 108.00,  0, 'Olivia Brooks',    'BUSINESS',        0),
    ('Small Company, Big Impact','9780000000039',  69.90, 11, 'Olivia Brooks',    'BUSINESS',        0),
    ('The Practical Negotiator', '9780000000040',  84.50,  5, 'Henry Scott',      'BUSINESS',        0),

    ('A Life in Motion',         '9780000000041',  62.90,  8, 'Emma Lewis',       'BIOGRAPHY',       0),
    ('Across Every Border',      '9780000000042',  78.00,  0, 'Emma Lewis',       'BIOGRAPHY',       0),
    ('The Scientist Who Persisted','9780000000043',95.90, 13, 'George Allen',     'BIOGRAPHY',       0),
    ('Notes from the Summit',    '9780000000044',  71.50,  4, 'George Allen',     'BIOGRAPHY',       0),
    ('An Unfinished Journey',    '9780000000045',  55.90, 16, 'Sofia Martin',     'BIOGRAPHY',       0),

    ('Love in the Rain',         '9780000000046',  39.90, 25, 'Grace Monroe',     'ROMANCE',         0),
    ('One More September',       '9780000000047',  47.90, 10, 'Grace Monroe',     'ROMANCE',         0),
    ('The Bookshop Promise',     '9780000000048',  52.50,  0, 'Chloe James',      'ROMANCE',         0),
    ('Meet Me by the Sea',       '9780000000049',  66.00,  6, 'Chloe James',      'ROMANCE',         0),
    ('A Second First Chance',    '9780000000050',  58.90, 18, 'Lily Foster',      'ROMANCE',         0)
ON CONFLICT (isbn) DO NOTHING;
