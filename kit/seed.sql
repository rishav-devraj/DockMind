-- DockMind seed data. Adapt table and column names to your own schema.
-- Quantities are numeric, units are plain strings. Do not rely on SKU being printed on delivery notes.
INSERT INTO purchase_orders (id, po_number, supplier, status) VALUES
  ('PO-4501', 'PO-4501', 'Fresh Farm Produce Ltd', 'OPEN'),
  ('PO-4502', 'PO-4502', 'Blue Ocean Seafood Co.',  'OPEN'),
  ('PO-4503', 'PO-4503', 'Dairy Direct',            'OPEN'),
  ('PO-4513', 'PO-4513', 'Green Valley Eggs',       'OPEN');
INSERT INTO po_lines (id, po_id, sku, description, ordered_qty, unit) VALUES
  ('POL-4501-1', 'PO-4501', 'SKU-APL-01', 'Gala Apples',        200, 'KG'),
  ('POL-4501-2', 'PO-4501', 'SKU-ORG-02', 'Oranges, Navel',     150, 'KG'),
  ('POL-4501-3', 'PO-4501', 'SKU-LET-03', 'Lettuce, Romaine',    60, 'CASE'),
  ('POL-4502-1', 'PO-4502', 'SKU-SAL-10', 'Salmon fillet',       80, 'KG'),
  ('POL-4502-2', 'PO-4502', 'SKU-PRW-11', 'Tiger Prawns',        40, 'KG'),
  ('POL-4503-1', 'PO-4503', 'SKU-MLK-20', 'Milk 1L',            240, 'EA'),
  ('POL-4503-2', 'PO-4503', 'SKU-BTR-21', 'Butter 500g',        100, 'EA'),
  ('POL-4503-3', 'PO-4503', 'SKU-CHS-22', 'Cheddar, mature',     40, 'KG'),
  ('POL-4513-1', 'PO-4513', 'SKU-EGG-30', 'Eggs, large, tray',  120, 'TRAY');
