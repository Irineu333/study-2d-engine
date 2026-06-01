# debug-ui-shell

## ADDED Requirements

### Requirement: Tema único de debug
O subsistema de debug SHALL expor um `DebugTheme` como fonte única da aparência
de painel (cor de fundo, cor e espessura de borda, margens, paddings e escala
de texto nomeada), e todo painel de debug SHALL derivar sua chrome dele.

#### Scenario: Painéis compartilham a mesma chrome
- **WHEN** dois painéis de debug distintos são desenhados
- **THEN** ambos usam a mesma cor de fundo, borda e margens vindas do `DebugTheme`

#### Scenario: Cores de gizmo/log vêm do tema
- **WHEN** um gizmo ou o log overlay precisa de uma cor
- **THEN** a cor é resolvida pela fonte única do tema (não por literal local)

### Requirement: Layout por slot de canto
O subsistema de debug SHALL expor um `DebugDock` per-tree que posiciona cada
`ScreenDebugWidget` por um `DockSlot` declarado (canto ou centro), empilhando
verticalmente os widgets de um mesmo slot a partir da borda, com gutter do tema.

#### Scenario: Widgets do mesmo slot empilham sem sobrepor
- **WHEN** dois `ScreenDebugWidget` declaram o mesmo `DockSlot` e estão enabled
- **THEN** o dock os posiciona empilhados verticalmente, sem sobreposição

#### Scenario: Layout re-flui no resize
- **WHEN** o `tree.size` muda
- **THEN** o dock recalcula os origins de cada widget, mantendo-os dentro do
  viewport e ancorados ao seu canto

#### Scenario: Widget novo declara um slot, não pixels
- **WHEN** um novo `ScreenDebugWidget` é registrado com um `DockSlot`
- **THEN** ele aparece empilhado naquele slot sem nenhum pixel hardcoded

### Requirement: Widget reporta tamanho; dock dá o origin
Cada `ScreenDebugWidget` SHALL reportar o tamanho que ocupa (medido a partir do
conteúdo) e SHALL desenhar a partir do origin fornecido pelo `DebugDock`, sem
posicionar-se por pixels absolutos próprios.

#### Scenario: Painel de altura variável re-empilha
- **WHEN** um widget de altura variável (ex.: picker, log) muda de tamanho
- **THEN** o dock re-empilha os widgets seguintes no slot a partir do novo tamanho

#### Scenario: Sem origin próprio hardcoded
- **WHEN** um `ScreenDebugWidget` é desenhado
- **THEN** sua posição vem do dock, não de uma constante de canto interna
