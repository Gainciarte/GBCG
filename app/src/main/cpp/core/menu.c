/*
 * Copyright (C) 2017-2020 Philip Jones
 * Copyright (C) 2026 Gustavo Inciarte
 *
 * Licensed under the MIT License.
 * See either the LICENSE file, or:
 *
 * https://opensource.org/licenses/MIT
 *
 */

/*
 * GBCG's copy of the core's menu.c (app/libs/gbcc/src/menu.c), compiled instead of it.
 * The only change is that the menu's texts go through gbcc_tr(), so they follow the
 * app's language. gbcc_tr() (in gbcc.cpp) returns Latin-1 text, which the core's
 * font can draw, or the English text itself if there's no translation.
 */

#include "gbcc.h"
#include "debug.h"
#include "input.h"
#include "menu.h"
#include "nelem.h"
#include <stdio.h>
#include <string.h>

static void toggle_option(struct gbcc *gbc, enum gbcc_key key);
static const char *bool2str(bool b);
static char selected(struct gbcc_menu *menu, enum GBCC_MENU_ENTRY entry);
static int modulo(int x, int n);
static void turbo_index(struct gbcc *gbc, bool inc);
static void menu_line(char *dst, char marker, const char *label, const char *value);

const char *gbcc_tr(const char *text);
void gbcc_request_reset(void);

/*
 * The menu has room for twelve rows (the screen is 144 pixels high, the font 12), so
 * GBCG's "Reset" takes the row of the FPS counter, which is also in the app's settings.
 * It asks for A twice, so a game isn't lost by mistake.
 */
static bool reset_armed = false;

/* Each line is 32 characters: a marker, the label in 13 columns and the value in 18 */
#define MENU_LABEL_WIDTH 13
#define MENU_VALUE_WIDTH 18
#define MENU_LINE_WIDTH (1 + MENU_LABEL_WIDTH + MENU_VALUE_WIDTH)

static const float turbo_speeds[] = {0, 0.25f, 0.5f, 1.5f, 2, 3, 4, 5, 10};

void gbcc_menu_init(struct gbcc *gbc)
{
	struct gbcc_menu *menu = &gbc->menu;
	*menu = (struct gbcc_menu){0};

	menu->save_state = 1;
	menu->load_state = 1;

	menu->width = GBC_SCREEN_WIDTH / gbc->window.font.tile_width;
	menu->height = GBC_SCREEN_HEIGHT / gbc->window.font.tile_height;
	menu->text = calloc(menu->width * menu->height + 1, 1);
	menu->initialised = true;
}

void gbcc_menu_destroy(struct gbcc *gbc)
{
	struct gbcc_menu *menu = &gbc->menu;
	if (!menu->initialised) {
		gbcc_log_error("Menu not initialised!\n");
		return;
	}
	free(menu->text);
	menu->initialised = false;
}

void gbcc_menu_update(struct gbcc *gbc)
{
	struct gbcc_menu *menu = &gbc->menu;
	if (!menu->initialised) {
		gbcc_log_error("Menu not initialised!\n");
		return;
	}
	if (!gbc->core.initialised) {
		return;
	}

	const char *link_text;
	switch (gbc->core.link_cable.state) {
		case GBCC_LINK_CABLE_STATE_DISCONNECTED:
			link_text = gbcc_tr("Disconnected");
			break;
		case GBCC_LINK_CABLE_STATE_LOOPBACK:
			link_text = gbcc_tr("Loopback");
			break;
		case GBCC_LINK_CABLE_STATE_PRINTER:
			link_text = gbcc_tr("Printer");
			break;
		default:
			link_text = "";
			break;
	}

	char turbo_text[MENU_VALUE_WIDTH + 1];
	if (gbc->turbo_speed == 0) {
		strncpy(turbo_text, gbcc_tr("Unlimited"), sizeof(turbo_text) - 1);
		turbo_text[sizeof(turbo_text) - 1] = '\0';
	} else {
		snprintf(turbo_text, sizeof(turbo_text), "%gx", gbc->turbo_speed);
	}

	/* The menu always has room for the 12 lines (the screen is 32x12 characters) */
	if (menu->width < MENU_LINE_WIDTH || menu->height < GBCC_MENU_ENTRY_NUM_ENTRIES) {
		return;
	}
	const char *states = "1 2 3 4 5 6 7 8 9 ";
	char *line = menu->text;
	menu_line(line, selected(menu, GBCC_MENU_ENTRY_SAVE_STATE), gbcc_tr("Save state:"), states);
	menu_line(line += MENU_LINE_WIDTH, selected(menu, GBCC_MENU_ENTRY_LOAD_STATE), gbcc_tr("Load state:"), states);
	menu_line(line += MENU_LINE_WIDTH, selected(menu, GBCC_MENU_ENTRY_AUTOSAVE), gbcc_tr("Autosave:"), bool2str(gbc->autosave));
	menu_line(line += MENU_LINE_WIDTH, selected(menu, GBCC_MENU_ENTRY_FRAMEBLEND), gbcc_tr("Frameblend:"), bool2str(gbc->frame_blending));
	menu_line(line += MENU_LINE_WIDTH, selected(menu, GBCC_MENU_ENTRY_LINK_CABLE), gbcc_tr("Link cable:"), link_text);
	menu_line(line += MENU_LINE_WIDTH, selected(menu, GBCC_MENU_ENTRY_SHADER), gbcc_tr("Shader:"),
			gbcc_tr(gbc->window.gl.shaders[gbc->window.gl.cur_shader].name));
	menu_line(line += MENU_LINE_WIDTH, selected(menu, GBCC_MENU_ENTRY_TURBO_MULT), gbcc_tr("Turbo mult:"), turbo_text);
	menu_line(line += MENU_LINE_WIDTH, selected(menu, GBCC_MENU_ENTRY_VSYNC), gbcc_tr("Vsync:"), bool2str(gbc->core.sync_to_video));
	menu_line(line += MENU_LINE_WIDTH, selected(menu, GBCC_MENU_ENTRY_INTERLACING), gbcc_tr("Interlacing:"), bool2str(gbc->interlacing));
	menu_line(line += MENU_LINE_WIDTH, selected(menu, GBCC_MENU_ENTRY_FPS_COUNTER), gbcc_tr("Reset:"),
			reset_armed ? gbcc_tr("Press A again") : gbcc_tr("Press A"));
	menu_line(line += MENU_LINE_WIDTH, selected(menu, GBCC_MENU_ENTRY_CHEATS), gbcc_tr("Cheats:"), bool2str(gbc->core.cheats.enabled));
	menu_line(line += MENU_LINE_WIDTH, selected(menu, GBCC_MENU_ENTRY_PALETTE), gbcc_tr("Palette:"),
			gbcc_tr(gbc->core.ppu.palette.name));
}

/*
 * Writes one line of the menu, MENU_LINE_WIDTH characters plus the terminator (which the
 * next line overwrites). Labels longer than 12 characters are cut, so a space always
 * separates them from their value and the save state boxes line up.
 */
void menu_line(char *dst, char marker, const char *label, const char *value)
{
	snprintf(dst, MENU_LINE_WIDTH + 1, "%c%-*.*s %-*.*s", marker,
			MENU_LABEL_WIDTH - 1, MENU_LABEL_WIDTH - 1, label,
			MENU_VALUE_WIDTH, MENU_VALUE_WIDTH, value);
}

void gbcc_menu_process_key(struct gbcc *gbc, enum gbcc_key key)
{
	struct gbcc_menu *menu = &gbc->menu;

	if (key != GBCC_KEY_A && key != GBCC_KEY_START) {
		reset_armed = false;
	}
	switch (key) {
		case GBCC_KEY_UP:
			if (menu->selection == 0) {
				menu->selection = GBCC_MENU_ENTRY_NUM_ENTRIES;
			}
			menu->selection--;
			break;
		case GBCC_KEY_DOWN:
			if (menu->selection == GBCC_MENU_ENTRY_NUM_ENTRIES - 1) {
				menu->selection = 0;
			} else {
				menu->selection++;
			}
			break;
		case GBCC_KEY_LEFT:
		case GBCC_KEY_RIGHT:
		case GBCC_KEY_A:
		case GBCC_KEY_START:
			toggle_option(gbc, key);
			break;
		case GBCC_KEY_MENU:
		case GBCC_KEY_B:
			menu->show = false;
			break;
		default:
			break;
	}
	gbcc_menu_update(gbc);
}

void toggle_option(struct gbcc *gbc, enum gbcc_key key)
{
	struct gbcc_menu *menu = &gbc->menu;

	switch (menu->selection) {
		case GBCC_MENU_ENTRY_SAVE_STATE:
			if (key == GBCC_KEY_LEFT) {
				menu->save_state--;
			} else if (key == GBCC_KEY_RIGHT) {
				menu->save_state++;
			} else {
				gbc->save_state = menu->save_state;
				menu->show = false;
			}
			menu->save_state = (int8_t)modulo(menu->save_state - 1, 9) + 1;
			break;
		case GBCC_MENU_ENTRY_LOAD_STATE:
			if (key == GBCC_KEY_LEFT) {
				menu->load_state--;
			} else if (key == GBCC_KEY_RIGHT) {
				menu->load_state++;
			} else {
				gbc->load_state = menu->load_state;
				menu->show = false;
			}
			menu->load_state = (int8_t)modulo(menu->load_state - 1, 9) + 1;
			break;
		case GBCC_MENU_ENTRY_AUTOSAVE:
			gbc->autosave ^= 1;
			break;
		case GBCC_MENU_ENTRY_FRAMEBLEND:
			gbc->frame_blending ^= 1;
			break;
		case GBCC_MENU_ENTRY_LINK_CABLE:
			if (key == GBCC_KEY_LEFT) {
				gbc->core.link_cable.state--;
			} else {
				gbc->core.link_cable.state++;
			}
			if (gbc->core.link_cable.state < 0) {
				gbc->core.link_cable.state += GBCC_LINK_CABLE_STATE_NUM_STATES;
			}
			gbc->core.link_cable.state %= GBCC_LINK_CABLE_STATE_NUM_STATES;
			break;
		case GBCC_MENU_ENTRY_SHADER:
			if (key == GBCC_KEY_LEFT) {
				gbc->window.gl.cur_shader--;
			} else {
				gbc->window.gl.cur_shader++;
			}
			gbc->window.gl.cur_shader = modulo(
					gbc->window.gl.cur_shader,
					N_ELEM(gbc->window.gl.shaders));
			break;
		case GBCC_MENU_ENTRY_TURBO_MULT:
			if (key == GBCC_KEY_LEFT) {
				turbo_index(gbc, false);
			} else {
				turbo_index(gbc, true);
			}
			break;
		case GBCC_MENU_ENTRY_VSYNC:
			gbc->core.sync_to_video ^= 1;
			break;
		case GBCC_MENU_ENTRY_INTERLACING:
			gbc->interlacing ^= 1;
			break;
		case GBCC_MENU_ENTRY_FPS_COUNTER:
			if (key != GBCC_KEY_A && key != GBCC_KEY_START) {
				break;
			}
			if (!reset_armed) {
				reset_armed = true;
			} else {
				reset_armed = false;
				menu->show = false;
				gbcc_request_reset();
			}
			break;
		case GBCC_MENU_ENTRY_PALETTE:
			{
				unsigned int p_idx = gbcc_get_palette_index(gbc->core.ppu.palette.name);
				if (key == GBCC_KEY_LEFT) {
					p_idx = p_idx + GBCC_NUM_PALETTES - 1;
				} else {
					p_idx++;
				}
				gbc->core.ppu.palette = gbcc_get_palette_by_index(p_idx % GBCC_NUM_PALETTES);
			}
			break;
		case GBCC_MENU_ENTRY_CHEATS:
			gbc->core.cheats.enabled ^= 1;
			break;
		case GBCC_MENU_ENTRY_NUM_ENTRIES:
			break;
	}
}

const char *bool2str(bool b)
{
	return b ? gbcc_tr("On") : gbcc_tr("Off");
}

char selected(struct gbcc_menu *menu, enum GBCC_MENU_ENTRY entry)
{
	return menu->selection == entry ? '>' : ' ';
}

int modulo(int x, int n)
{
	return (x % n + abs(n)) % n;
}

void turbo_index(struct gbcc *gbc, bool inc)
{
	const float mult = gbc->turbo_speed;
	const int len = N_ELEM(turbo_speeds);
	int idx;
	if (inc) {
		for (idx = len - 1; idx > 0 && turbo_speeds[idx] > mult; idx--);
		idx++;
	} else {
		for (idx = 0; idx < len && turbo_speeds[idx] < mult; idx++);
		idx--;
	}
	gbc->turbo_speed = turbo_speeds[modulo(idx, len)];
}
