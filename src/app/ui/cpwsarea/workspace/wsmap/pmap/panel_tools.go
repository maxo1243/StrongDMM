package pmap

import (
	"github.com/SpaiR/imgui-go"
	"github.com/go-gl/glfw/v3.3/glfw"
	"sdmm/app/ui/cpwsarea/workspace/wsmap/pmap/tools"
	"sdmm/imguiext"
	w "sdmm/imguiext/widget"
)

type toolDesc struct {
	icon string
	help string
}

const tSeparator = "toolsSeparator"

var (
	toolsOrder = []string{
		tools.TNAdd,
		tSeparator,
		tools.TNSelect,
		tools.TNDelete,
	}

	toolsDesc = map[string]toolDesc{
		tools.TNAdd: {
			icon: imguiext.IconFaPlus,
			help: "Add (1)\nClick - Place selected object topmost\nAlt+Click - Place selected object with replace",
		},
		tools.TNSelect: {
			icon: imguiext.IconFaEyeDropper,
			help: "Select (Hold S)\nClick - Select hovered object",
		},
		tools.TNDelete: {
			icon: imguiext.IconFaEraser,
			help: "Delete (Hold D)\nClick - Delete hovered object\nAlt+Click - Delete tile",
		},
	}
)

func (p *PaneMap) showToolsPanel() {
	for idx, toolName := range toolsOrder {
		if idx > 0 || idx < len(toolsOrder)-1 {
			imgui.SameLine()
		}

		if toolName == tSeparator {
			imgui.TextDisabled("|")
			continue
		}

		tool := p.tools.Tools()[toolName]
		desc := toolsDesc[toolName]

		btn := w.Button(desc.icon, func() {
			tools.SetSelected(toolName)
		})
		if p.tools.Selected() == tool {
			if tool.AltBehaviour() {
				btn.Style(imguiext.StyleButtonRed{})
			} else {
				btn.Style(imguiext.StyleButtonGreen{})
			}
		}
		btn.Build()

		imguiext.SetItemHoveredTooltip(desc.help)
	}
}

func (p *PaneMap) processTempToolsMode() {
	if !p.tmpIsInTemporalToolMode {
		p.tmpLastSelectedToolName = p.tools.Selected().Name()
	}

	var inMode bool
	inMode = inMode || p.processTempToolMode(int(glfw.KeyS), -1, tools.TNSelect)
	inMode = inMode || p.processTempToolMode(int(glfw.KeyD), -1, tools.TNDelete)

	if p.tmpIsInTemporalToolMode && !inMode {
		tools.SetSelected(p.tmpLastSelectedToolName)
		p.tmpLastSelectedToolName = ""
		p.tmpIsInTemporalToolMode = false
	}
}

func (p *PaneMap) processTempToolMode(key, altKey int, modeName string) bool {
	// Ignore presses when Dear ImGui inputs are in charge or actual shortcuts are invisible.
	if !p.shortcuts.Visible() {
		return false
	}

	isKeyPressed := imgui.IsKeyPressedV(key, false) || imgui.IsKeyPressedV(altKey, false)
	isKeyReleased := imgui.IsKeyReleased(key) || imgui.IsKeyReleased(altKey)
	isKeyDown := imgui.IsKeyDown(key) || imgui.IsKeyDown(altKey)
	isSelected := tools.IsSelected(modeName)

	if isKeyPressed && !isSelected {
		p.tmpPrevSelectedToolName = p.tools.Selected().Name()
		p.tmpIsInTemporalToolMode = true
		tools.SetSelected(modeName)
	} else if isKeyReleased && len(p.tmpPrevSelectedToolName) != 0 {
		if isSelected {
			tools.SetSelected(p.tmpPrevSelectedToolName)
		}
		p.tmpPrevSelectedToolName = ""
	}

	return isKeyDown
}

func (p *PaneMap) selectAddTool() {
	tools.SetSelected(tools.TNAdd)
}
