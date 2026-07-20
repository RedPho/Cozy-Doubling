# Implementation Plan - Backend Supporter Logic Alignment

We will update the backend PostgreSQL functions to align with the new "Supporter Pass" logic. Currently, the `equip_item` function fails for premium themes because it strictly checks the `user_inventory` table, ignoring the `is_supporter` status that should grant access to all premium themes.

## Proposed Changes

### Database: PostgreSQL Functions (SQL)

#### [MODIFY] `equip_item` Function
- Update the logic to allow equipping a theme if:
    1.  The user explicitly owns it in `user_inventory` (Basic themes).
    2.  **OR** The item is marked as `is_premium` and the user's profile has `is_supporter = true`.

#### [MODIFY] `buy_item` Function (Optional/Refinement)
- Ensure consistency in how premium items are treated. If a user is a supporter, they shouldn't necessarily need to "buy" a premium theme with leaves if the pass unlocks it.

---

## SQL Update Script

```sql
-- Update equip_item to recognize Supporter status
CREATE OR REPLACE FUNCTION public.equip_item(target_item_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
declare
  item_is_premium boolean;
  user_is_supporter boolean;
begin
  -- 1. Fetch item and user status
  select is_premium into item_is_premium from public.items where id = target_item_id;
  select is_supporter into user_is_supporter from public.profiles where id = auth.uid();

  -- 2. SECURITY CHECK: Do you own it OR are you a supporter for a premium item?
  if not (
    exists (select 1 from public.user_inventory where user_id = auth.uid() and item_id = target_item_id)
    OR (item_is_premium AND user_is_supporter)
  ) then
    raise exception 'You do not own this item and do not have an active Supporter Pass.';
  end if;

  -- 3. VALIDATION: Is this item actually a theme?
  if not exists (
    select 1 from public.items
    where id = target_item_id and category_id = 'theme'
  ) then
    raise exception 'This item is not a theme.';
  end if;

  -- 4. APPLY: Update the profile
  update public.profiles
  set equipped_theme_id = target_item_id
  where id = auth.uid();
end;
$function$;
```

## Verification Plan

### Manual Verification
- Run the SQL update in the Supabase SQL Editor.
- In the app, mock a user as `is_supporter = true` (manually update the DB row for your user).
- Attempt to equip a **Premium Theme** (e.g., Midnight Blue) from the Inventory.
- Verify it equips successfully without throwing the "You do not own this item" error.
- Verify that a **Basic Theme** (not in inventory) still correctly throws the error if the user hasn't bought it with leaves.
